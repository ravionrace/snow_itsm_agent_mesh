package com.acme.itsm.orchestrator.chat;

import com.acme.itsm.orchestrator.guard.InputScreen;
import com.acme.itsm.orchestrator.guard.PiiRedactor;
import com.acme.itsm.orchestrator.guard.UserRateLimiter;
import com.acme.itsm.orchestrator.runtime.AgentRuntimeClient;
import com.acme.itsm.orchestrator.security.CurrentUser;
import com.acme.itsm.orchestrator.security.ServiceNowTokenBroker;
import com.acme.itsm.orchestrator.session.SessionStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Every chat channel (the web chat API and the ServiceNow channel) goes through here, so input screening, PII
 * redaction, rate limits, session ownership and the per-user ServiceNow token are applied the same way.
 *
 * <p>Work is split in two: {@code prepare*} validates and is fast (errors surface immediately), {@code run}
 * calls the agent runtime and can take a minute; the ServiceNow channel runs it in the background.
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final ServiceNowTokenBroker tokens;
    private final SessionStore sessions;
    private final PiiRedactor redactor;
    private final InputScreen screen;
    private final UserRateLimiter limiter;
    private final AgentRuntimeClient runtime;
    private final ObjectMapper mapper;

    public ChatService(ServiceNowTokenBroker tokens, SessionStore sessions, PiiRedactor redactor, InputScreen screen,
                       UserRateLimiter limiter, AgentRuntimeClient runtime, ObjectMapper mapper) {
        this.tokens = tokens;
        this.sessions = sessions;
        this.redactor = redactor;
        this.screen = screen;
        this.limiter = limiter;
        this.runtime = runtime;
        this.mapper = mapper;
    }

    /** A validated unit of work, ready to run against the agent runtime. */
    public interface Prepared {
        String sessionId();

        JsonNode run();
    }

    /** Screens and redacts a new message; creates the session when sessionId is null. */
    public Prepared prepareTurn(CurrentUser.User user, String sessionId, String message) {
        rateLimit(user);
        InputScreen.Verdict verdict = screen.screen(message);
        if (!verdict.allowed()) {
            log.info("input refused user={} reason={} score={}", user.id(), verdict.reason(), verdict.score());
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Message not accepted: " + verdict.reason());
        }
        SessionStore.ChatSession session = sessionId == null ? sessions.create(user.id()) : owned(user, sessionId);
        PiiRedactor.Result redacted = redactor.redact(verdict.text(), session.vault());
        if (!redacted.vault().equals(session.vault())) {
            sessions.saveVault(session.id(), redacted.vault());
        }
        return prepared(session.id(), () -> forDisplay(
                runtime.turn(session.id(), identity(user), redacted.text(), redacted.vault()), redacted.vault(), redacted.removed()));
    }

    /** The user's Confirm / Reject on a pending change. */
    public Prepared prepareDecision(CurrentUser.User user, String sessionId, String pendingId, String decision) {
        rateLimit(user);
        if (!"confirm".equals(decision) && !"reject".equals(decision)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "decision must be confirm or reject");
        }
        SessionStore.ChatSession session = owned(user, sessionId);
        return prepared(session.id(), () -> forDisplay(
                runtime.decide(session.id(), pendingId, identity(user), decision, session.vault()), session.vault(), List.of()));
    }

    public JsonNode history(CurrentUser.User user, String sessionId) {
        SessionStore.ChatSession session = owned(user, sessionId);
        return rehydrate(runtime.history(session.id(), identity(user)), session.vault());
    }

    /** Live steps of the user's running turn; empty if unavailable (progress is a nicety, never an error). */
    public JsonNode progress(CurrentUser.User user, String sessionId) {
        try {
            SessionStore.ChatSession session = owned(user, sessionId);
            JsonNode steps = runtime.progress(session.id(), user.id()).path("steps");
            return steps.isArray() ? rehydrate(steps, session.vault()) : mapper.createArrayNode();
        } catch (RuntimeException e) {
            log.debug("progress unavailable session={}: {}", sessionId, e.getMessage());
            return mapper.createArrayNode();
        }
    }

    private SessionStore.ChatSession owned(CurrentUser.User user, String sessionId) {
        return sessions.find(sessionId, user.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "session not found"));
    }

    private AgentRuntimeClient.Identity identity(CurrentUser.User user) {
        return new AgentRuntimeClient.Identity(user.id(), tokens.tokenFor(user));
    }

    private void rateLimit(CurrentUser.User user) {
        if (!limiter.tryAcquire(user.id())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many requests, please wait a moment.");
        }
    }

    private static Prepared prepared(String sessionId, java.util.function.Supplier<JsonNode> work) {
        return new Prepared() {
            public String sessionId() { return sessionId; }
            public JsonNode run() { return work.get(); }
        };
    }

    /** The user sees their own values again; placeholders exist only for the model. */
    private JsonNode forDisplay(JsonNode result, Map<String, String> vault, List<String> removed) {
        ObjectNode out = (ObjectNode) rehydrate(result, vault);
        if (!removed.isEmpty()) {
            out.put("notice", "Sensitive data (" + String.join(", ", new TreeSet<>(removed))
                    + ") was removed from your message before processing.");
        }
        return out;
    }

    private JsonNode rehydrate(JsonNode node, Map<String, String> vault) {
        if (vault.isEmpty() || node == null) {
            return node;
        }
        try {
            String json = mapper.writeValueAsString(node);
            for (Map.Entry<String, String> e : vault.entrySet()) {
                String escaped = mapper.writeValueAsString(e.getValue());
                json = json.replace(e.getKey(), escaped.substring(1, escaped.length() - 1));
            }
            return mapper.readTree(json);
        } catch (Exception e) {
            return node;
        }
    }
}
