package com.scruttlabs.snow.client.http;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;

/**
 * Exponential backoff with full jitter: before retry {@code n} (1-based) wait a random time in
 * {@code [0, min(cap, base * 2^(n-1)))}. Spreading retries randomly keeps many crawl threads that failed at the
 * same moment from retrying at the same moment again.
 *
 * <p>A {@code Retry-After} header raises the wait to at least what the server asked for, capped at {@code cap}.
 */
public final class RetryPolicy {

    private final int maxRetries;
    private final Duration base;
    private final Duration cap;
    private final DoubleSupplier random;

    /**
     * @param random returns values in [0, 1); tests pass a fixed value
     */
    public RetryPolicy(int maxRetries, Duration base, Duration cap, DoubleSupplier random) {
        if (maxRetries < 0) throw new IllegalArgumentException("maxRetries must be >= 0");
        this.maxRetries = maxRetries;
        this.base = Objects.requireNonNull(base, "base");
        this.cap = Objects.requireNonNull(cap, "cap");
        this.random = Objects.requireNonNull(random, "random");
        if (base.isNegative() || base.isZero()) throw new IllegalArgumentException("base must be positive");
        if (cap.compareTo(base) < 0) throw new IllegalArgumentException("cap must be >= base");
    }

    /** 500 ms base, 30 s cap. ThreadLocalRandom is looked up per call, so each thread uses its own generator. */
    public static RetryPolicy defaults(int maxRetries) {
        return new RetryPolicy(maxRetries, Duration.ofMillis(500), Duration.ofSeconds(30),
                () -> ThreadLocalRandom.current().nextDouble());
    }

    /** 429 Too Many Requests and the gateway / unavailable family. Other 4xx will fail again, so they are final. */
    public static boolean isRetryableStatus(int status) {
        return status == 429 || status == 502 || status == 503 || status == 504;
    }

    public int maxRetries() {
        return maxRetries;
    }

    /** Wait before retry {@code retry} (1 = first retry). */
    public Duration backoff(int retry, Optional<Duration> retryAfter) {
        if (retry < 1) throw new IllegalArgumentException("retry is 1-based");
        // base * 2^(retry-1), without overflowing for large retry numbers
        long ceilingMillis = cap.toMillis();
        int shift = Math.min(retry - 1, 30);
        long exponential = base.toMillis() << shift;
        if (exponential > 0) ceilingMillis = Math.min(ceilingMillis, exponential);
        Duration jittered = Duration.ofMillis((long) (random.getAsDouble() * ceilingMillis));

        if (retryAfter.isEmpty()) return jittered;
        Duration asked = retryAfter.get().compareTo(cap) > 0 ? cap : retryAfter.get();
        return asked.compareTo(jittered) > 0 ? asked : jittered;
    }

    /** Parses Retry-After: delta-seconds ("120") or an HTTP date ("Wed, 21 Oct 2026 07:28:00 GMT"). */
    public static Optional<Duration> parseRetryAfter(String header, Clock clock) {
        if (header == null || header.isBlank()) return Optional.empty();
        String value = header.strip();
        try {
            long seconds = Long.parseLong(value);
            return seconds < 0 ? Optional.empty() : Optional.of(Duration.ofSeconds(seconds));
        } catch (NumberFormatException notSeconds) {
            try {
                Instant at = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
                Duration wait = Duration.between(clock.instant(), at);
                return Optional.of(wait.isNegative() ? Duration.ZERO : wait);
            } catch (DateTimeParseException notADate) {
                return Optional.empty();
            }
        }
    }
}
