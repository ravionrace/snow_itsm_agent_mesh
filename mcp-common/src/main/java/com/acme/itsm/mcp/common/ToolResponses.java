package com.acme.itsm.mcp.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;

/**
 * The single JSON envelope every tool returns. The harness in the orchestrator keys off {@code status}:
 * <ul>
 *   <li>{@code OK} - read result or committed write, {@code data} holds the fields</li>
 *   <li>{@code PENDING_CONFIRMATION} - a write preview; nothing has changed in ServiceNow yet</li>
 *   <li>{@code BLOCKED} - a coded business rule refused the action</li>
 *   <li>{@code ERROR} - the call failed; {@code next_step} says what to do</li>
 * </ul>
 */
public final class ToolResponses {

    private final ObjectMapper mapper;

    public ToolResponses(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String ok(Object data) {
        ObjectNode root = mapper.createObjectNode();
        root.put("status", "OK");
        root.set("data", mapper.valueToTree(data));
        return write(root);
    }

    public String committed(Object data, String confirmationId) {
        ObjectNode root = mapper.createObjectNode();
        root.put("status", "OK");
        root.put("committed", true);
        root.put("confirmation_id", confirmationId);
        root.set("data", mapper.valueToTree(data));
        return write(root);
    }

    public String pending(String confirmationId, Instant expiresAt, Preview preview) {
        ObjectNode root = mapper.createObjectNode();
        root.put("status", "PENDING_CONFIRMATION");
        root.put("confirmation_id", confirmationId);
        root.put("expires_at", expiresAt.toString());
        root.set("preview", mapper.valueToTree(preview));
        root.put("message", "Nothing has been changed yet. The user must review this preview and click Confirm.");
        return write(root);
    }

    public String failure(ToolException e) {
        ObjectNode root = mapper.createObjectNode();
        root.put("status", e.kind() == ToolException.Kind.BLOCKED ? "BLOCKED" : "ERROR");
        root.put("code", e.code());
        root.put("message", e.getMessage());
        if (e.nextStep() != null) {
            root.put("next_step", e.nextStep());
        }
        return write(root);
    }

    private String write(ObjectNode node) {
        try {
            return mapper.writeValueAsString(node);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
