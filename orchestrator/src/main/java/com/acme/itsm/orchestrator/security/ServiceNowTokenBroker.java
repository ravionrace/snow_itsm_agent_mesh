package com.acme.itsm.orchestrator.security;

import com.acme.itsm.orchestrator.OrchestratorProperties;
import com.fasterxml.jackson.databind.JsonNode;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;

import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

/**
 * Turns the user's SSO session into a ServiceNow OAuth token for that same user. Agents always act as the
 * signed-in person: ACLs, catalog entitlements and the audit trail follow them, and the per-API read/write
 * roles decide what the Access agent offers.
 *
 * <p>SIGNED_ASSERTION is the production mode (the mesh is a confidential OAuth client; see
 * docs/servicenow-oauth.md). Tokens are cached per user until shortly before they expire.
 */
@Component
public class ServiceNowTokenBroker {

    private final OrchestratorProperties.ServiceNow props;
    private final UserTokenCache cache;
    private final SignedAssertionTokenIssuer issuer;
    private final RestClient rest;

    public ServiceNowTokenBroker(OrchestratorProperties props, RestClient.Builder builder, MeterRegistry meters) {
        this.props = props.getServicenow();
        this.cache = new UserTokenCache(this.props.getRefreshMargin(), this.props.getTokenCacheMaxUsers(), Clock.systemUTC());
        this.issuer = this.props.getTokenMode() == OrchestratorProperties.TokenMode.SIGNED_ASSERTION
                ? new SignedAssertionTokenIssuer(this.props, builder.clone(), Clock.systemUTC(), meters) : null;
        this.rest = builder.build();
        meters.gauge("itsm.servicenow.token.cached_users", cache, UserTokenCache::size);
    }

    public String tokenFor(CurrentUser.User user) {
        return switch (props.getTokenMode()) {
            case SIGNED_ASSERTION -> signedAssertion(user);
            case PASSTHROUGH -> require(user.idpToken());
            case STATIC -> require(props.getStaticToken());
            case JWT_BEARER -> cache.get("idp:" + user.id(), k -> exchangeIdpToken(require(user.idpToken())));
        };
    }

    /** Forget a user's cached token (e.g. after an account change); the next call mints a new one. */
    public void invalidate(CurrentUser.User user) {
        cache.invalidate(user.snSubject());
        cache.invalidate("idp:" + user.id());
    }

    private String signedAssertion(CurrentUser.User user) {
        try {
            return cache.get(user.snSubject(), issuer::mint);
        } catch (SignedAssertionTokenIssuer.TokenException e) {
            // "rejected": ServiceNow refused this user (unknown/inactive/locked, or a configuration problem).
            throw new ResponseStatusException("rejected".equals(e.reason()) ? UNAUTHORIZED : SERVICE_UNAVAILABLE,
                    "rejected".equals(e.reason())
                            ? "ServiceNow did not accept your account for the assistant. Contact your ServiceNow administrator."
                            : "ServiceNow is not reachable right now. Please try again shortly.");
        }
    }

    private UserTokenCache.Token exchangeIdpToken(String idpJwt) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", SignedAssertionTokenIssuer.GRANT_TYPE);
        form.add("assertion", idpJwt);
        form.add("client_id", props.getClientId());
        form.add("client_secret", props.resolvedClientSecret());
        try {
            JsonNode body = rest.post().uri(props.tokenEndpoint())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            String token = body == null ? null : body.path("access_token").asText(null);
            if (token == null) {
                throw new ResponseStatusException(UNAUTHORIZED, "ServiceNow did not issue a token");
            }
            return new UserTokenCache.Token(token, java.time.Instant.now().plusSeconds(Math.max(60, body.path("expires_in").asLong(1800))));
        } catch (RestClientResponseException e) {
            throw new ResponseStatusException(UNAUTHORIZED, "ServiceNow token exchange failed");
        }
    }

    private static String require(String token) {
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(UNAUTHORIZED, "No ServiceNow credential for this user");
        }
        return token;
    }
}
