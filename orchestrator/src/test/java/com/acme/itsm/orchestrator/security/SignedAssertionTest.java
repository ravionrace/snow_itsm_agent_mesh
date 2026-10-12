package com.acme.itsm.orchestrator.security;

import com.acme.itsm.orchestrator.OrchestratorProperties;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SignedAssertionTest {

    @TempDir
    Path dir;
    KeyPair keys;
    OrchestratorProperties.ServiceNow cfg;
    final Instant now = Instant.parse("2026-10-05T10:00:00Z");
    final Clock clock = Clock.fixed(now, ZoneOffset.UTC);

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        keys = gen.generateKeyPair();
        Path key = dir.resolve("key.pem");
        Files.writeString(key, "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(keys.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n");
        Path secret = dir.resolve("secret");
        Files.writeString(secret, "s3cr3t-from-file\n");
        cfg = new OrchestratorProperties.ServiceNow();
        cfg.setInstanceUrl("https://acme.service-now.com/");
        cfg.setClientId("client-123");
        cfg.setClientSecretFile(secret.toString());
        cfg.setPrivateKeyFile(key.toString());
        cfg.setKeyId("mesh-2026-10");
    }

    @Test
    void assertionIsSignedShortLivedAndNamesTheUser() throws Exception {
        var issuer = new SignedAssertionTokenIssuer(cfg, RestClient.create(), clock, new SimpleMeterRegistry());
        SignedJWT jwt = SignedJWT.parse(issuer.assertion("jo.smith"));
        assertThat(jwt.verify(new RSASSAVerifier((RSAPublicKey) keys.getPublic()))).isTrue();
        assertThat(jwt.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);
        assertThat(jwt.getHeader().getKeyID()).isEqualTo("mesh-2026-10");
        var c = jwt.getJWTClaimsSet();
        assertThat(c.getSubject()).isEqualTo("jo.smith");
        assertThat(c.getIssuer()).isEqualTo("client-123");
        assertThat(c.getAudience()).containsExactly("client-123");
        assertThat(c.getExpirationTime().toInstant()).isEqualTo(now.plusSeconds(60));
        assertThat(c.getJWTID()).isNotBlank();
        assertThat(SignedJWT.parse(issuer.assertion("jo.smith")).getJWTClaimsSet().getJWTID()).isNotEqualTo(c.getJWTID());
    }

    @Test
    void mintPostsTheJwtBearerGrantAsTheConfidentialClient() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        Map<String, String> form = new HashMap<>();
        server.expect(requestTo("https://acme.service-now.com/oauth_token.do"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(req -> {
                    String body = ((org.springframework.mock.http.client.MockClientHttpRequest) req).getBodyAsString();
                    for (String pair : body.split("&")) {
                        String[] kv = pair.split("=", 2);
                        form.put(kv[0], URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
                    }
                })
                .andRespond(withSuccess("{\"access_token\":\"tok-1\",\"expires_in\":900,\"token_type\":\"Bearer\"}",
                        MediaType.APPLICATION_JSON));
        var issuer = new SignedAssertionTokenIssuer(cfg, builder.build(), clock, new SimpleMeterRegistry());

        var token = issuer.mint("jo.smith");

        assertThat(token.value()).isEqualTo("tok-1");
        assertThat(token.expiresAt()).isEqualTo(now.plusSeconds(900));
        assertThat(form).containsEntry("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
                .containsEntry("client_id", "client-123").containsEntry("client_secret", "s3cr3t-from-file");
        assertThat(form.get("assertion")).startsWith("ey");
        server.verify();
    }

    @Test
    void rejectedUserAndOutageAreDistinguished() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://acme.service-now.com/oauth_token.do")).andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_JSON).body("{\"error\":\"access_denied\",\"error_description\":\"user not found\"}"));
        server.expect(requestTo("https://acme.service-now.com/oauth_token.do")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        var meters = new SimpleMeterRegistry();
        var issuer = new SignedAssertionTokenIssuer(cfg, builder.build(), clock, meters);

        assertThatThrownBy(() -> issuer.mint("ghost")).isInstanceOf(SignedAssertionTokenIssuer.TokenException.class)
                .hasMessageContaining("user not found").extracting("reason").isEqualTo("rejected");
        assertThatThrownBy(() -> issuer.mint("jo")).extracting("reason").isEqualTo("unavailable");
        assertThat(meters.counter("itsm.servicenow.token.failures", "reason", "rejected").count()).isEqualTo(1);
    }

    @Test
    void legacyPkcs1KeysAndSmallKeysAreRefused() throws Exception {
        Path pkcs1 = dir.resolve("rsa.pem");
        Files.writeString(pkcs1, "-----BEGIN RSA PRIVATE KEY-----\nAAAA\n-----END RSA PRIVATE KEY-----\n");
        assertThatThrownBy(() -> SignedAssertionTokenIssuer.loadPrivateKey(pkcs1.toString())).hasMessageContaining("PKCS#8");

        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(1024);
        Path small = dir.resolve("small.pem");
        Files.writeString(small, "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getEncoder().encodeToString(gen.generateKeyPair().getPrivate().getEncoded()) + "\n-----END PRIVATE KEY-----");
        assertThatThrownBy(() -> SignedAssertionTokenIssuer.loadPrivateKey(small.toString())).hasMessageContaining("2048");
    }

    @Test
    void cacheReusesTokensRefreshesBeforeExpiryAndSharesOneMint() throws Exception {
        var time = new java.util.concurrent.atomic.AtomicReference<>(now);
        Clock moving = new Clock() {
            public ZoneOffset getZone() { return ZoneOffset.UTC; }
            public Clock withZone(java.time.ZoneId z) { return this; }
            public Instant instant() { return time.get(); }
        };
        var cache = new UserTokenCache(Duration.ofMinutes(5), 100, moving);
        AtomicInteger mints = new AtomicInteger();
        CountDownLatch release = new CountDownLatch(1);
        java.util.function.Function<String, UserTokenCache.Token> slowMint = s -> {
            mints.incrementAndGet();
            try { release.await(); } catch (InterruptedException e) { throw new IllegalStateException(e); }
            return new UserTokenCache.Token("t" + mints.get(), time.get().plusSeconds(1800));
        };

        var pool = Executors.newFixedThreadPool(8);
        var results = new java.util.ArrayList<java.util.concurrent.Future<String>>();
        for (int i = 0; i < 8; i++) {
            results.add(pool.submit(() -> cache.get("jo", slowMint)));
        }
        Thread.sleep(100);
        release.countDown();
        for (var r : results) {
            assertThat(r.get()).isEqualTo("t1");
        }
        pool.shutdown();
        assertThat(mints).hasValue(1);                                          // single flight

        time.set(now.plusSeconds(1800 - 301));
        assertThat(cache.get("jo", slowMint)).isEqualTo("t1");                  // still outside the margin
        time.set(now.plusSeconds(1800 - 299));
        assertThat(cache.get("jo", slowMint)).isEqualTo("t2");                  // refreshed before expiry

        assertThatThrownBy(() -> cache.get("bad", s -> { throw new IllegalStateException("nope"); }))
                .hasMessage("nope");
        assertThat(cache.get("bad", s -> new UserTokenCache.Token("ok", time.get().plusSeconds(900)))).isEqualTo("ok"); // failures not cached
    }
}
