package com.scruttlabs.snow.client.http;

import java.io.IOException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The instance answered with an error status. Built from ServiceNow's error body,
 * {@code {"error": {"message": "...", "detail": "..."}, "status": "failure"}}, when there is one.
 */
public class SnowApiException extends RuntimeException {

    private final int status;
    private final String errorMessage;
    private final String detail;

    public SnowApiException(int status, String errorMessage, String detail) {
        super("ServiceNow returned " + status + ": " + errorMessage
                + (detail == null || detail.isBlank() ? "" : " (" + detail + ")"));
        this.status = status;
        this.errorMessage = errorMessage;
        this.detail = detail == null ? "" : detail;
    }

    /** Parses the error body; falls back to a generic message when the body is not ServiceNow's error JSON. */
    public static SnowApiException fromResponse(int status, String body, ObjectMapper json) {
        try {
            JsonNode error = json.readTree(body == null ? "" : body).path("error");
            String message = error.path("message").asText("");
            if (!message.isBlank()) return new SnowApiException(status, message, error.path("detail").asText(""));
        } catch (IOException | RuntimeException ignored) {
            // not JSON: fall through
        }
        return new SnowApiException(status, "HTTP " + status, "");
    }

    public int status() {
        return status;
    }

    public String errorMessage() {
        return errorMessage;
    }

    public String detail() {
        return detail;
    }

    /** Worth retrying: rate limited or a temporary server-side failure. */
    public boolean retryable() {
        return RetryPolicy.isRetryableStatus(status);
    }
}
