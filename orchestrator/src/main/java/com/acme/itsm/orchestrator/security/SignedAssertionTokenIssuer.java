package com.acme.itsm.orchestrator.security;

import com.acme.itsm.orchestrator.OrchestratorProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

/**
 * Obtains a ServiceNow access token for one user with the OAuth 2.0 JWT bearer grant (RFC 7523).
 *
 * <p>The mesh is a confidential client: it authenticates with its client id and secret, and proves the
 * assertion with its own RSA key, whose certificate is registered in the instance's JWT verifier map under
 * {@code kid}. The assertion is short-lived and single-use (jti), and names the user in {@code sub}; ServiceNow
 * matches it to sys_user with the registry's user field and issues a token for that user, so ACLs,
 * entitlements and the audit trail all apply to the real person.
 */
public class SignedAssertionTokenIssuer {

    static final String GRANT_TYPE = "urn:ietf:params:oauth:grant-type:jwt-bearer";
    private static final Logger log = LoggerFactory.getLogger(SignedAssertionTokenIssuer.class);

    private final OrchestratorProperties.ServiceNow cfg;
    private final RSASSASigner signer;
    private final String clientSecret;
    private final RestClient rest;
    private final Clock clock;
    private final Timer mintTimer;
    private final MeterRegistry meters;

    public SignedAssertionTokenIssuer(OrchestratorProperties.ServiceNow cfg, RestClient.Builder builder, Clock clock,
                                      MeterRegistry meters) {
        this(cfg, builder.requestFactory(timeouts(cfg)).build(), clock, meters);
    }

    SignedAssertionTokenIssuer(OrchestratorProperties.ServiceNow cfg, RestClient rest, Clock clock, MeterRegistry meters) {
        this.cfg = cfg;
        this.clock = clock;
        this.meters = meters;
        this.signer = new RSASSASigner(loadPrivateKey(cfg.getPrivateKeyFile()));
        this.clientSecret = cfg.resolvedClientSecret();
        if (cfg.getClientId() == null || cfg.getClientId().isBlank() || clientSecret == null || clientSecret.isBlank()) {
            throw new IllegalStateException("itsm.servicenow.client-id and client secret are required for SIGNED_ASSERTION");
        }
        this.rest = rest;
        this.mintTimer = Timer.builder("itsm.servicenow.token.mint").description("JWT bearer token requests")
                .register(meters);
    }

    private static JdkClientHttpRequestFactory timeouts(OrchestratorProperties.ServiceNow cfg) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(cfg.getConnectTimeout()).build());
        factory.setReadTimeout(cfg.getReadTimeout());
        return factory;
    }

    /** A fresh token for {@code subject} (the user's ServiceNow identifier). */
    public UserTokenCache.Token mint(String subject) {
        return mintTimer.record(() -> {
            try {
                MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
                form.add("grant_type", GRANT_TYPE);
                form.add("client_id", cfg.getClientId());
                form.add("client_secret", clientSecret);
                form.add("assertion", assertion(subject));
                JsonNode body = rest.post().uri(cfg.tokenEndpoint())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .accept(MediaType.APPLICATION_JSON)
                        .body(form)
                        .retrieve()
                        .body(JsonNode.class);
                String token = body == null ? null : body.path("access_token").asText(null);
                if (token == null || token.isBlank()) {
                    throw failure("no_token", "ServiceNow returned no access token", null);
                }
                long ttl = Math.max(60, body.path("expires_in").asLong(1800));
                meters.counter("itsm.servicenow.token.issued").increment();
                return new UserTokenCache.Token(token, clock.instant().plusSeconds(ttl));
            } catch (RestClientResponseException e) {
                // ServiceNow answers {"error": "...", "error_description": "..."}; neither contains secrets.
                String reason = e.getStatusCode().is4xxClientError() ? "rejected" : "unavailable";
                throw failure(reason, describe(e), e);
            } catch (ResourceAccessException e) {
                throw failure("unreachable", "ServiceNow token endpoint unreachable: " + e.getMessage(), e);
            }
        });
    }

    String assertion(String subject) {
        Instant now = clock.instant();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(cfg.getClientId())
                .subject(subject)
                .audience(cfg.resolvedAudience())
                .issueTime(Date.from(now))
                .notBeforeTime(Date.from(now.minusSeconds(5)))
                .expirationTime(Date.from(now.plus(cfg.getAssertionTtl())))
                .jwtID(UUID.randomUUID().toString())
                .build();
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(cfg.getKeyId()).type(JOSEObjectType.JWT).build();
        SignedJWT jwt = new SignedJWT(header, claims);
        try {
            jwt.sign(signer);
        } catch (JOSEException e) {
            throw new IllegalStateException("Could not sign the ServiceNow assertion", e);
        }
        return jwt.serialize();
    }

    private TokenException failure(String reason, String message, Exception cause) {
        meters.counter("itsm.servicenow.token.failures", "reason", reason).increment();
        log.warn("ServiceNow token request failed ({}): {}", reason, message);
        return new TokenException(reason, message, cause);
    }

    private static String describe(RestClientResponseException e) {
        try {
            JsonNode n = new com.fasterxml.jackson.databind.ObjectMapper().readTree(e.getResponseBodyAsString());
            String err = n.path("error").asText("");
            String desc = n.path("error_description").asText("");
            return "HTTP " + e.getStatusCode().value() + (err.isEmpty() ? "" : " " + err) + (desc.isEmpty() ? "" : ": " + desc);
        } catch (Exception ignored) {
            return "HTTP " + e.getStatusCode().value();
        }
    }

    static RSAPrivateKey loadPrivateKey(String file) {
        if (file == null || file.isBlank()) {
            throw new IllegalStateException("itsm.servicenow.private-key-file is required for SIGNED_ASSERTION");
        }
        try {
            String pem = Files.readString(Path.of(file), StandardCharsets.US_ASCII);
            if (pem.contains("BEGIN RSA PRIVATE KEY")) {
                throw new IllegalStateException("The private key must be PKCS#8 (BEGIN PRIVATE KEY). Convert it with: "
                        + "openssl pkcs8 -topk8 -nocrypt -in key.pem -out key.pk8.pem");
            }
            String base64 = pem.replaceAll("-----(BEGIN|END) PRIVATE KEY-----", "").replaceAll("\\s", "");
            RSAPrivateKey key = (RSAPrivateKey) KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
            if (key.getModulus().bitLength() < 2048) {
                throw new IllegalStateException("The ServiceNow signing key must be RSA 2048 bits or larger");
            }
            return key;
        } catch (IOException | GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Cannot load the ServiceNow signing key from " + file + ": " + e.getMessage(), e);
        }
    }

    /** A token could not be obtained. {@code reason}: rejected | unavailable | unreachable | no_token. */
    public static class TokenException extends RuntimeException {
        private final String reason;

        TokenException(String reason, String message, Throwable cause) {
            super(message, cause);
            this.reason = reason;
        }

        public String reason() {
            return reason;
        }
    }
}
