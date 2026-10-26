package com.scruttlabs.snow.client.auth;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

/** HTTP Basic authentication. The header is computed once; a 401 is final, so there is nothing to invalidate. */
public final class BasicAuth implements AuthStrategy {

    private final String header;

    public BasicAuth(String username, String password) {
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(password, "password");
        this.header = "Basic " + Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String authorizationHeader() {
        return header;
    }

    @Override
    public String toString() {
        return "BasicAuth[<redacted>]";
    }
}
