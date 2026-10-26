package com.scruttlabs.snow.client.auth;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scruttlabs.snow.client.SnowClientConfig;
import com.scruttlabs.snow.client.http.SnowApiException;

/**
 * OAuth 2.0 resource-owner password grant against {@code /oauth_token.do}, with the token cached by a
 * {@link TokenProvider}. The OAuth application on the instance must allow the password grant.
 */
public final class OAuthPasswordAuth implements AuthStrategy {

    /** Refresh this long before the token expires. */
    public static final Duration DEFAULT_REFRESH_SKEW = Duration.ofSeconds(60);
    /** Used when the token response has no expires_in. ServiceNow's default lifespan is 30 minutes. */
    private static final Duration DEFAULT_LIFESPAN = Duration.ofMinutes(30);
    private static final String BEARER = "Bearer ";

    /** OAuth client and integration user. {@link #toString()} never prints secrets. */
    public record Credentials(String clientId, String clientSecret, String username, String password) {

        public Credentials {
            Objects.requireNonNull(clientId, "clientId");
            Objects.requireNonNull(clientSecret, "clientSecret");
            Objects.requireNonNull(username, "username");
            Objects.requireNonNull(password, "password");
        }

        @Override
        public String toString() {
            return "Credentials[clientId=" + clientId + ", username=" + username + ", secrets=<redacted>]";
        }
    }

    private final SnowClientConfig config;
    private final HttpClient http;
    private final ObjectMapper json;
    private final Credentials credentials;
    private final Clock clock;
    private final TokenProvider tokens;

    public OAuthPasswordAuth(SnowClientConfig config, HttpClient http, ObjectMapper json, Credentials credentials) {
        this(config, http, json, credentials, Clock.systemUTC(), DEFAULT_REFRESH_SKEW);
    }

    public OAuthPasswordAuth(SnowClientConfig config, HttpClient http, ObjectMapper json, Credentials credentials,
                             Clock clock, Duration refreshSkew) {
        this.config = Objects.requireNonNull(config, "config");
        this.http = Objects.requireNonNull(http, "http");
        this.json = Objects.requireNonNull(json, "json");
        this.credentials = Objects.requireNonNull(credentials, "credentials");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.tokens = new TokenProvider(this::fetchToken, clock, refreshSkew);
    }

    @Override
    public String authorizationHeader() {
        return BEARER + tokens.current().value();
    }

    @Override
    public void onUnauthorized(String rejectedHeader) {
        if (rejectedHeader != null && rejectedHeader.startsWith(BEARER)) {
            tokens.invalidate(rejectedHeader.substring(BEARER.length()));
        }
    }

    private AccessToken fetchToken() {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "password");
        form.put("client_id", credentials.clientId());
        form.put("client_secret", credentials.clientSecret());
        form.put("username", credentials.username());
        form.put("password", credentials.password());
        String body = form.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));

        HttpRequest request = HttpRequest.newBuilder(config.resolve("/oauth_token.do", null))
                .timeout(config.requestTimeout())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new UncheckedIOException("OAuth token request failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted during the OAuth token request", e);
        }
        if (response.statusCode() != 200) {
            // The body can echo request details; keep only the OAuth error fields.
            throw new SnowApiException(response.statusCode(), "OAuth token request was rejected",
                    oauthError(response.body()));
        }
        return parse(response.body());
    }

    private AccessToken parse(String body) {
        JsonNode node;
        try {
            node = json.readTree(body);
        } catch (IOException e) {
            throw new SnowApiException(200, "OAuth token response is not JSON", "");
        }
        String value = node.path("access_token").asText("");
        if (value.isBlank()) throw new SnowApiException(200, "OAuth token response has no access_token", "");
        long seconds = node.path("expires_in").asLong(0);
        Duration lifespan = seconds > 0 ? Duration.ofSeconds(seconds) : DEFAULT_LIFESPAN;
        return new AccessToken(value, clock.instant().plus(lifespan));
    }

    private String oauthError(String body) {
        try {
            JsonNode node = json.readTree(body);
            return (node.path("error").asText("") + " " + node.path("error_description").asText("")).strip();
        } catch (IOException | RuntimeException e) {
            return "";
        }
    }
}
