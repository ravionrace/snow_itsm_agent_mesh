package com.acme.itsm.mcp.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.util.Map;
import java.util.function.Function;

/**
 * Calls this server's Scripted REST API in the scoped app, always with the signed-in user's token.
 *
 * <p>Reads are retried once on a transport error or 5xx. Writes are never retried: a timed-out write
 * may still have committed, so the agent is told to check before trying again. Every write carries an
 * idempotency key that the scoped app records in its audit table.
 */
public class ServiceNowClient {

    private static final Logger log = LoggerFactory.getLogger(ServiceNowClient.class);

    private final RestClient rest;
    private final RateLimiter rateLimiter;
    private final ObjectMapper mapper;

    public ServiceNowClient(McpCommonProperties.ServiceNow props, RateLimiter rateLimiter, ObjectMapper mapper) {
        if (props.getInstanceUrl() == null || props.getApi() == null) {
            throw new IllegalStateException("itsm.mcp.servicenow.instance-url and itsm.mcp.servicenow.api are required");
        }
        HttpClient http = HttpClient.newBuilder().connectTimeout(props.getConnectTimeout()).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(props.getReadTimeout());
        this.rest = RestClient.builder()
                .baseUrl(props.getInstanceUrl().replaceAll("/+$", "") + props.basePath())
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.rateLimiter = rateLimiter;
        this.mapper = mapper;
    }

    /** Package-private for tests. */
    ServiceNowClient(RestClient rest, RateLimiter rateLimiter, ObjectMapper mapper) {
        this.rest = rest;
        this.rateLimiter = rateLimiter;
        this.mapper = mapper;
    }

    public JsonNode get(CallerContext caller, String path) {
        return get(caller, path, Map.of());
    }

    public JsonNode get(CallerContext caller, String path, Map<String, ?> query) {
        Function<UriBuilder, URI> uri = b -> {
            b.path(path);
            query.forEach((k, v) -> {
                if (v != null) {
                    b.queryParam(k, v);
                }
            });
            return b.build();
        };
        try {
            return exchange(caller, HttpMethod.GET, uri, null, null);
        } catch (TransientFailure first) {
            log.warn("Retrying read {} after transient failure: {}", path, first.getMessage());
            try {
                return exchange(caller, HttpMethod.GET, uri, null, null);
            } catch (TransientFailure second) {
                throw new ToolException(ToolException.Kind.ERROR, "UPSTREAM_UNAVAILABLE",
                        "ServiceNow did not respond in time.",
                        "Tell the user ServiceNow is not responding and to try again shortly.");
            }
        }
    }

    public JsonNode post(CallerContext caller, String path, Object body, String idempotencyKey) {
        return write(caller, HttpMethod.POST, path, body, idempotencyKey);
    }

    public JsonNode patch(CallerContext caller, String path, Object body, String idempotencyKey) {
        return write(caller, HttpMethod.PATCH, path, body, idempotencyKey);
    }

    private JsonNode write(CallerContext caller, HttpMethod method, String path, Object body, String idempotencyKey) {
        try {
            return exchange(caller, method, b -> b.path(path).build(), body, idempotencyKey);
        } catch (TransientFailure e) {
            throw new ToolException(ToolException.Kind.ERROR, "WRITE_OUTCOME_UNKNOWN",
                    "ServiceNow did not confirm the write; it may or may not have been applied.",
                    "Read the record again to check its current state before doing anything else. Never repeat the write blindly.");
        }
    }

    private JsonNode exchange(CallerContext caller, HttpMethod method, Function<UriBuilder, URI> uri,
                              Object body, String idempotencyKey) {
        rateLimiter.acquire();
        try {
            RestClient.RequestBodySpec spec = rest.method(method)
                    .uri(uri)
                    .headers(h -> {
                        h.setBearerAuth(caller.bearerToken());
                        if (caller.requestId() != null) h.set("X-Agent-Request-Id", caller.requestId());
                        if (caller.sessionId() != null) h.set("X-Agent-Session-Id", caller.sessionId());
                        if (idempotencyKey != null) h.set("X-Idempotency-Key", idempotencyKey);
                    });
            if (body != null) {
                spec.contentType(MediaType.APPLICATION_JSON).body(body);
            }
            String raw = spec.retrieve().body(String.class);
            JsonNode root = raw == null || raw.isBlank() ? mapper.createObjectNode() : mapper.readTree(raw);
            // Scripted REST wraps the payload in {"result": ...}
            return root.has("result") ? root.get("result") : root;
        } catch (RestClientResponseException e) {
            throw translate(e);
        } catch (ResourceAccessException e) {
            throw new TransientFailure(e.getMessage());
        } catch (java.io.IOException e) {
            throw new ToolException(ToolException.Kind.ERROR, "BAD_RESPONSE", "ServiceNow returned an unreadable response.",
                    "Tell the user the request could not be completed.");
        }
    }

    private RuntimeException translate(RestClientResponseException e) {
        HttpStatusCode status = e.getStatusCode();
        String snMessage = extractMessage(e.getResponseBodyAsString());
        return switch (status.value()) {
            case 400 -> ToolException.invalid(snMessage != null ? snMessage : "ServiceNow rejected the request.",
                    "Fix the arguments described in the message and call again.");
            case 401 -> new ToolException(ToolException.Kind.ERROR, "UNAUTHENTICATED",
                    "The user's ServiceNow session is not valid.", "Ask the user to sign in again. Do not retry.");
            case 403 -> new ToolException(ToolException.Kind.ERROR, "FORBIDDEN",
                    snMessage != null ? snMessage : "The signed-in user is not allowed to do this.",
                    "Tell the user they lack permission. Do not try another way to do the same thing.");
            case 404 -> ToolException.notFound("The record", "Check the number with the user or search for it.");
            case 409 -> new ToolException(ToolException.Kind.ERROR, "CONFLICT",
                    snMessage != null ? snMessage : "The record changed or the action conflicts with its current state.",
                    "Read the record again and show the user its current state.");
            case 422 -> ToolException.blocked("PLATFORM_RULE",
                    snMessage != null ? snMessage : "A ServiceNow business rule or data policy rejected the change.",
                    "Explain the rule to the user. Do not try to work around it.");
            case 429 -> new ToolException(ToolException.Kind.ERROR, "RATE_LIMITED", "ServiceNow is throttling requests.",
                    "Tell the user to try again in a minute.");
            default -> status.is5xxServerError() ? new TransientFailure("HTTP " + status.value())
                    : new ToolException(ToolException.Kind.ERROR, "UPSTREAM_ERROR", "Unexpected ServiceNow response " + status.value(),
                    "Tell the user the request failed.");
        };
    }

    private String extractMessage(String body) {
        try {
            JsonNode n = mapper.readTree(body);
            JsonNode err = n.path("error");
            String msg = err.path("message").asText(null);
            String detail = err.path("detail").asText(null);
            if (msg == null) {
                return null;
            }
            return detail == null || detail.isBlank() ? msg : msg + ": " + detail;
        } catch (Exception ignored) {
            return null;
        }
    }

    /** A retryable failure for reads; turned into an "outcome unknown" error for writes. */
    static final class TransientFailure extends RuntimeException {
        TransientFailure(String message) {
            super(message);
        }
    }
}
