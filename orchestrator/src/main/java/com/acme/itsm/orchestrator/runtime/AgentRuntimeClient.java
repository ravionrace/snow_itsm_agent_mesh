package com.acme.itsm.orchestrator.runtime;

import com.acme.itsm.orchestrator.OrchestratorProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

/** Calls the LangGraph agent runtime with the user's identity and ServiceNow token. */
@Component
public class AgentRuntimeClient {

    private final RestClient rest;
    private final String apiKey;

    public AgentRuntimeClient(OrchestratorProperties props, RestClient.Builder builder) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(props.getRuntime().getTimeout());
        this.rest = builder.baseUrl(props.getRuntime().getUrl()).requestFactory(factory).build();
        this.apiKey = props.getRuntime().getApiKey();
    }

    public record Identity(String userId, String snToken) {
    }

    public JsonNode turn(String sessionId, Identity who, String message, Map<String, String> vault) {
        return post("/v1/sessions/{id}/turns", sessionId, null, who, Map.of("message", message, "pii_vault", vault));
    }

    public JsonNode decide(String sessionId, String pendingId, Identity who, String decision, Map<String, String> vault) {
        return post("/v1/sessions/{id}/confirmations/{pid}", sessionId, pendingId, who,
                Map.of("decision", decision, "pii_vault", vault));
    }

    public JsonNode history(String sessionId, Identity who) {
        try {
            return rest.get().uri("/v1/sessions/{id}", sessionId).headers(h -> headers(h, who)).retrieve().body(JsonNode.class);
        } catch (RestClientResponseException e) {
            throw translate(e);
        }
    }

    /** Steps of the turn running in this session (routing, access, thinking, tool calls); needs no SN token. */
    public JsonNode progress(String sessionId, String userId) {
        try {
            return rest.get().uri("/v1/sessions/{id}/progress", sessionId)
                    .headers(h -> { h.set("X-Runtime-Key", apiKey); h.set("X-User-Id", userId); })
                    .retrieve().body(JsonNode.class);
        } catch (RestClientResponseException e) {
            throw translate(e);
        }
    }

    private JsonNode post(String path, String sessionId, String pendingId, Identity who, Object body) {
        try {
            return rest.post().uri(path, pendingId == null ? new Object[]{sessionId} : new Object[]{sessionId, pendingId})
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(h -> headers(h, who))
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            throw translate(e);
        }
    }

    private void headers(org.springframework.http.HttpHeaders h, Identity who) {
        h.set("X-Runtime-Key", apiKey);
        h.set("X-User-Id", who.userId());
        h.set("X-SN-Token", who.snToken());
    }

    private static ResponseStatusException translate(RestClientResponseException e) {
        HttpStatus status = switch (e.getStatusCode().value()) {
            case 404 -> HttpStatus.NOT_FOUND;
            case 409 -> HttpStatus.CONFLICT;
            case 400, 422 -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.BAD_GATEWAY;
        };
        return new ResponseStatusException(status, status == HttpStatus.BAD_GATEWAY ? "agent runtime error" : e.getStatusText());
    }
}
