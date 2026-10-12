package com.acme.itsm.orchestrator.api;

import com.acme.itsm.orchestrator.chat.ChatService;
import com.acme.itsm.orchestrator.security.CurrentUser;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Chat API for the web chat, Teams bot or portal widget (users signed in with the corporate IdP).
 * ServiceNow's in-app Assistant uses the signed server-to-server channel instead (ServiceNowChannelController).
 *
 * <pre>
 * POST /api/chat                                     {sessionId?, message}
 * POST /api/sessions/{id}/confirmations/{pendingId}  {decision: confirm|reject}
 * GET  /api/sessions/{id}
 * </pre>
 */
@RestController
@RequestMapping("/api")
public class ChatController {

    private final CurrentUser currentUser;
    private final ChatService chat;

    public ChatController(CurrentUser currentUser, ChatService chat) {
        this.currentUser = currentUser;
        this.chat = chat;
    }

    public record ChatRequest(@Pattern(regexp = "^[A-Za-z0-9_-]{8,64}$") String sessionId, @NotBlank String message) {
    }

    public record DecisionRequest(@Pattern(regexp = "^(confirm|reject)$") String decision) {
    }

    @PostMapping("/chat")
    public JsonNode chat(@Valid @RequestBody ChatRequest req, Authentication auth) {
        return chat.prepareTurn(currentUser.resolve(auth), req.sessionId(), req.message()).run();
    }

    @PostMapping("/sessions/{id}/confirmations/{pendingId}")
    public JsonNode decide(@PathVariable String id, @PathVariable String pendingId,
                           @Valid @RequestBody DecisionRequest req, Authentication auth) {
        return chat.prepareDecision(currentUser.resolve(auth), id, pendingId, req.decision()).run();
    }

    @GetMapping("/sessions/{id}")
    public JsonNode history(@PathVariable String id, Authentication auth) {
        return chat.history(currentUser.resolve(auth), id);
    }
}
