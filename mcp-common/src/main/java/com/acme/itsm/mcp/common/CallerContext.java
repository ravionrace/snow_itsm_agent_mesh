package com.acme.itsm.mcp.common;

import io.modelcontextprotocol.common.McpTransportContext;

/**
 * Who is calling this tool, extracted from the HTTP headers of the stateless MCP request.
 *
 * <p>The bearer token is the signed-in user's ServiceNow OAuth token. It is forwarded unchanged
 * to the scoped app so {@code gs.getUserID()} is the real person, never a service account.
 */
public record CallerContext(String bearerToken, String userId, String requestId, String sessionId,
                            String idempotencyKey) {

    public static final String KEY_TOKEN = "snow_token";
    public static final String KEY_USER = "user_id";
    public static final String KEY_REQUEST = "request_id";
    public static final String KEY_SESSION = "session_id";
    public static final String KEY_IDEMPOTENCY = "idempotency_key";

    public static CallerContext from(McpTransportContext ctx) {
        if (ctx == null) {
            throw ToolException.unauthenticated();
        }
        String token = (String) ctx.get(KEY_TOKEN);
        String user = (String) ctx.get(KEY_USER);
        if (token == null || token.isBlank() || user == null || user.isBlank()) {
            throw ToolException.unauthenticated();
        }
        return new CallerContext(token, user, (String) ctx.get(KEY_REQUEST), (String) ctx.get(KEY_SESSION),
                (String) ctx.get(KEY_IDEMPOTENCY));
    }

    @Override
    public String toString() {
        // Never print the token.
        return "CallerContext[user=" + userId + ", request=" + requestId + ", session=" + sessionId + "]";
    }
}
