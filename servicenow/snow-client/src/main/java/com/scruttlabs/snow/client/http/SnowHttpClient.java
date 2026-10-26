package com.scruttlabs.snow.client.http;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.scruttlabs.snow.client.SnowClientConfig;
import com.scruttlabs.snow.client.auth.AuthStrategy;

/**
 * JSON over HTTP to one ServiceNow instance. Thread-safe: share one instance between all crawl threads, since
 * the JDK HttpClient pools connections.
 *
 * <p>Each call:
 * <ol>
 *   <li>adds the Authorization header from the {@link AuthStrategy};</li>
 *   <li>on 401, tells the strategy which header was rejected and retries <b>once</b> (an expired or revoked token);</li>
 *   <li>on 429 / 502 / 503 / 504 or an I/O error, retries with {@link RetryPolicy} backoff, honouring Retry-After;</li>
 *   <li>on any other non-2xx, throws {@link SnowApiException} straight away;</li>
 *   <li>on 2xx, unwraps ServiceNow's {@code {"result": ...}} envelope (when present) and decodes the body.</li>
 * </ol>
 * If the thread is interrupted while backing off, the call stops, the interrupt flag is restored, and an
 * {@link IllegalStateException} is thrown.
 */
public final class SnowHttpClient {

    private static final Logger log = LoggerFactory.getLogger(SnowHttpClient.class);

    /** Sleeps between retries; tests replace it to record waits instead of sleeping. */
    @FunctionalInterface
    public interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    private final SnowClientConfig config;
    private final AuthStrategy auth;
    private final ObjectMapper json;
    private final HttpClient http;
    private final RetryPolicy retry;
    private final Sleeper sleeper;
    private final Clock clock;

    public SnowHttpClient(SnowClientConfig config, AuthStrategy auth, ObjectMapper json, HttpClient http) {
        this(config, auth, json, http, RetryPolicy.defaults(config.maxRetries()),
                d -> Thread.sleep(d.toMillis()), Clock.systemUTC());
    }

    public SnowHttpClient(SnowClientConfig config, AuthStrategy auth, ObjectMapper json, HttpClient http,
                          RetryPolicy retry, Sleeper sleeper, Clock clock) {
        this.config = Objects.requireNonNull(config, "config");
        this.auth = Objects.requireNonNull(auth, "auth");
        this.json = Objects.requireNonNull(json, "json");
        this.http = Objects.requireNonNull(http, "http");
        this.retry = Objects.requireNonNull(retry, "retry");
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** A JDK HttpClient with the configured connect timeout. Build it once and share it. */
    public static HttpClient newHttpClient(SnowClientConfig config) {
        return HttpClient.newBuilder()
                .connectTimeout(config.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /** snake_case names, unknown properties ignored, java.time support. Configure once, then share. */
    public static ObjectMapper newObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public TypeFactory types() {
        return json.getTypeFactory();
    }

    /** GET {@code path?query}; null or blank query values are left out. */
    public <T> T get(String path, Map<String, String> query, JavaType type) {
        URI uri = config.resolve(checkPath(path), encode(query));
        return execute("GET", uri, null, type);
    }

    /** POST {@code body} as JSON to {@code path}. */
    public <T> T post(String path, Object body, JavaType type) {
        String payload;
        try {
            payload = json.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Request body is not serializable", e);
        }
        return execute("POST", config.resolve(checkPath(path), null), payload, type);
    }

    private <T> T execute(String method, URI uri, String payload, JavaType type) {
        boolean reauthenticated = false;
        int retries = 0;
        while (true) {
            String authorization = null;
            HttpResponse<String> response = null;
            RuntimeException failure;
            Optional<Duration> retryAfter = Optional.empty();
            long started = System.nanoTime();
            try {
                authorization = auth.authorizationHeader();
                response = http.send(request(method, uri, payload, authorization), HttpResponse.BodyHandlers.ofString());
                failure = null;
            } catch (IOException e) {
                failure = new UncheckedIOException(method + " " + uri.getPath() + " failed: " + e.getMessage(), e);
            } catch (UncheckedIOException e) {
                failure = e;   // e.g. the OAuth token request could not connect
            } catch (SnowApiException e) {
                if (!e.retryable()) throw e;
                failure = e;   // e.g. the OAuth token endpoint answered 503
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted during " + method + " " + uri.getPath(), e);
            }

            if (response != null) {
                int status = response.statusCode();
                log.debug("{} {} -> {} in {} ms", method, uri, status, (System.nanoTime() - started) / 1_000_000);
                if (status / 100 == 2) return decode(response.body(), type, uri);
                if (status == 401 && !reauthenticated) {
                    reauthenticated = true;
                    auth.onUnauthorized(authorization);
                    log.info("{} {} -> 401, re-authenticating once", method, uri.getPath());
                    continue;
                }
                failure = SnowApiException.fromResponse(status, response.body(), json);
                if (!RetryPolicy.isRetryableStatus(status)) throw failure;
                retryAfter = RetryPolicy.parseRetryAfter(response.headers().firstValue("Retry-After").orElse(null), clock);
            }

            if (retries >= retry.maxRetries()) throw failure;
            retries++;
            Duration wait = retry.backoff(retries, retryAfter);
            log.warn("{} {} failed ({}); retry {}/{} in {} ms", method, uri.getPath(), failure.getMessage(),
                    retries, retry.maxRetries(), wait.toMillis());
            pause(wait, method, uri);
        }
    }

    private HttpRequest request(String method, URI uri, String payload, String authorization) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(config.requestTimeout())
                .header("Accept", "application/json")
                .header("Authorization", authorization);
        if (payload == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(payload));
            if (log.isTraceEnabled()) log.trace("{} {} payload={}", method, uri, Redactor.payload(payload));
        }
        return builder.build();
    }

    private void pause(Duration wait, String method, URI uri) {
        try {
            sleeper.sleep(wait);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to retry " + method + " " + uri.getPath(), e);
        }
    }

    private <T> T decode(String body, JavaType type, URI uri) {
        try {
            JsonNode root = json.readTree(body);
            JsonNode content = root != null && root.isObject() && root.has("result") ? root.get("result") : root;
            return json.readerFor(type).readValue(content);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot decode the response of " + uri.getPath() + " as " + type
                    + ": " + e.getMessage(), e);
        }
    }

    private static String checkPath(String path) {
        if (path == null || !path.startsWith("/api/")) {
            throw new IllegalArgumentException("ServiceNow API path must start with /api/: " + path);
        }
        return path;
    }

    private static String encode(Map<String, String> query) {
        if (query == null || query.isEmpty()) return "";
        return query.entrySet().stream()
                .filter(e -> e.getValue() != null && !e.getValue().isBlank())
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }
}
