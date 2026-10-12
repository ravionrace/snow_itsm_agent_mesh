package com.acme.itsm.orchestrator.session;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Chat sessions owned by the API: who owns the session and the PII placeholder vault. Conversation history
 * and agent state are LangGraph checkpoints in the runtime, keyed by the same session id.
 */
public interface SessionStore {

    record ChatSession(String id, String userId, Instant createdAt, Map<String, String> vault) {
    }

    ChatSession create(String userId);

    /** Only returns the session if it belongs to {@code userId}. */
    Optional<ChatSession> find(String id, String userId);

    void saveVault(String id, Map<String, String> vault);
}
