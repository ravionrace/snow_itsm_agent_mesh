package com.acme.itsm.orchestrator.channel;

import com.acme.itsm.orchestrator.chat.ChatService;
import com.acme.itsm.orchestrator.security.CurrentUser;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The ServiceNow in-app Assistant's entry point (called by the scoped app's /chat REST API, server to server).
 * Requests are HMAC-signed by the instance (ChannelSignatureFilter), which names the signed-in user; the agents
 * then act as that user with a per-user OAuth token.
 *
 * <pre>
 * POST /channel/servicenow/v1/turns                                   {session_id?, message} -> 202 {turn_id, session_id}
 * POST /channel/servicenow/v1/sessions/{sid}/confirmations/{pid}      {decision}             -> 202 {turn_id, session_id}
 * GET  /channel/servicenow/v1/turns/{turn_id}                         -> {status: running | done | failed, progress | result | error}
 * GET  /channel/servicenow/v1/sessions/{sid}                          -> transcript + pending confirmation
 * </pre>
 */
@RestController
@RequestMapping("/channel/servicenow/v1")
@ConditionalOnProperty(prefix = "itsm.channel", name = "enabled", havingValue = "true")
public class ServiceNowChannelController {

    private static final String ID = "^[A-Za-z0-9_-]{8,64}$";

    private final ChatService chat;
    private final TurnJobs jobs;

    public ServiceNowChannelController(ChatService chat, TurnJobs jobs) {
        this.chat = chat;
        this.jobs = jobs;
    }

    @PostMapping("/turns")
    public ResponseEntity<Map<String, Object>> startTurn(@RequestBody Map<String, String> body, HttpServletRequest req) {
        CurrentUser.User user = user(req);
        String sessionId = body.get("session_id");
        if (sessionId != null && !sessionId.matches(ID)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid session_id");
        }
        String message = body.getOrDefault("message", "");
        if (message.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message is required");
        }
        return accepted(user, chat.prepareTurn(user, sessionId, message));
    }

    @PostMapping("/sessions/{sessionId}/confirmations/{pendingId}")
    public ResponseEntity<Map<String, Object>> decide(@PathVariable String sessionId, @PathVariable String pendingId,
                                                      @RequestBody Map<String, String> body, HttpServletRequest req) {
        CurrentUser.User user = user(req);
        if (!sessionId.matches(ID) || !pendingId.matches("^[A-Za-z0-9]{8,64}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid id");
        }
        return accepted(user, chat.prepareDecision(user, sessionId, pendingId, body.get("decision")));
    }

    @GetMapping("/turns/{turnId}")
    public Map<String, Object> turn(@PathVariable String turnId, HttpServletRequest req) {
        TurnJobs.Job job = jobs.get(turnId, user(req).id());
        if (job == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown or expired turn");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("turn_id", job.id());
        out.put("session_id", job.sessionId());
        out.put("status", job.status().name().toLowerCase());
        if (job.status() == TurnJobs.Status.RUNNING) {
            out.put("progress", chat.progress(user(req), job.sessionId()));
        }
        if (job.result() != null) out.put("result", job.result());
        if (job.error() != null) {
            out.put("error", job.error());
            out.put("error_status", job.errorStatus());
        }
        return out;
    }

    @GetMapping("/sessions/{sessionId}")
    public JsonNode history(@PathVariable String sessionId, HttpServletRequest req) {
        if (!sessionId.matches(ID)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid session_id");
        }
        return chat.history(user(req), sessionId);
    }

    private ResponseEntity<Map<String, Object>> accepted(CurrentUser.User user, ChatService.Prepared work) {
        TurnJobs.Job job = jobs.submit(user.id(), work.sessionId(), work::run);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "turn_id", job.id(), "session_id", job.sessionId(), "status", "running"));
    }

    /** The ServiceNow user_name, verified by the signature filter. It is also the OAuth subject. */
    private static CurrentUser.User user(HttpServletRequest req) {
        String user = (String) req.getAttribute(ChannelSignatureFilter.USER_ATTRIBUTE);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return new CurrentUser.User(user, user, null);
    }
}
