package com.scruttlabs.snow.client.auth;

/**
 * How requests to the instance are authenticated. Implementations must be thread-safe: every crawl thread asks
 * for a header on every request.
 */
public interface AuthStrategy {

    /** Value for the Authorization header of the next request, e.g. "Bearer …" or "Basic …". */
    String authorizationHeader();

    /**
     * The instance answered 401 to a request sent with {@code rejectedHeader}. A token-based strategy drops that
     * token, if it is still the current one, so the next {@link #authorizationHeader()} fetches a new one.
     */
    default void onUnauthorized(String rejectedHeader) {
    }
}
