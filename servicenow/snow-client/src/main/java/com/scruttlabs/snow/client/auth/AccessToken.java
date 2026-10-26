package com.scruttlabs.snow.client.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** An OAuth access token and the moment it expires. {@link #toString()} never prints the token. */
public record AccessToken(String value, Instant expiresAt) {

    public AccessToken {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (value.isBlank()) throw new IllegalArgumentException("value is blank");
    }

    /** True once {@code now + skew} reaches the expiry, so callers refresh a little before the token runs out. */
    public boolean isExpiring(Clock clock, Duration skew) {
        return !clock.instant().plus(skew).isBefore(expiresAt);
    }

    @Override
    public String toString() {
        return "AccessToken[value=<redacted>, expiresAt=" + expiresAt + "]";
    }
}
