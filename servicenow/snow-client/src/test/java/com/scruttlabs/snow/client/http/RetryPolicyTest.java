package com.scruttlabs.snow.client.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class RetryPolicyTest {

    private static final Duration BASE = Duration.ofMillis(500);
    private static final Duration CAP = Duration.ofSeconds(30);

    /** random() just below 1 gives the top of each jitter window. */
    private final RetryPolicy max = new RetryPolicy(4, BASE, CAP, () -> 0.999_999);
    private final RetryPolicy min = new RetryPolicy(4, BASE, CAP, () -> 0.0);

    @Test
    void onlyRateLimitAndGatewayErrorsAreRetryable() {
        assertThat(RetryPolicy.isRetryableStatus(429)).isTrue();
        assertThat(RetryPolicy.isRetryableStatus(502)).isTrue();
        assertThat(RetryPolicy.isRetryableStatus(503)).isTrue();
        assertThat(RetryPolicy.isRetryableStatus(504)).isTrue();
        assertThat(RetryPolicy.isRetryableStatus(400)).isFalse();
        assertThat(RetryPolicy.isRetryableStatus(401)).isFalse();
        assertThat(RetryPolicy.isRetryableStatus(403)).isFalse();
        assertThat(RetryPolicy.isRetryableStatus(404)).isFalse();
        assertThat(RetryPolicy.isRetryableStatus(500)).isFalse();
    }

    @Test
    void windowDoublesPerRetryUpToTheCap() {
        assertThat(max.backoff(1, Optional.empty())).isEqualTo(Duration.ofMillis(499));
        assertThat(max.backoff(2, Optional.empty())).isEqualTo(Duration.ofMillis(999));
        assertThat(max.backoff(3, Optional.empty())).isEqualTo(Duration.ofMillis(1999));
        assertThat(max.backoff(7, Optional.empty())).isEqualTo(Duration.ofMillis(29_999));   // 32 s window, capped
        assertThat(max.backoff(200, Optional.empty())).isEqualTo(Duration.ofMillis(29_999)); // no shift overflow
    }

    @Test
    void fullJitterCanBeZero() {
        assertThat(min.backoff(5, Optional.empty())).isZero();
    }

    @Test
    void retryAfterIsAFloorCappedAtCap() {
        assertThat(min.backoff(1, Optional.of(Duration.ofSeconds(2)))).isEqualTo(Duration.ofSeconds(2));
        assertThat(max.backoff(7, Optional.of(Duration.ofSeconds(1)))).isEqualTo(Duration.ofMillis(29_999));
        assertThat(min.backoff(1, Optional.of(Duration.ofHours(1)))).isEqualTo(CAP);
    }

    @Test
    void parsesRetryAfterSecondsAndHttpDates() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-25T07:28:00Z"), ZoneOffset.UTC);

        assertThat(RetryPolicy.parseRetryAfter("120", clock)).contains(Duration.ofSeconds(120));
        assertThat(RetryPolicy.parseRetryAfter("Sun, 25 Oct 2026 07:28:30 GMT", clock)).contains(Duration.ofSeconds(30));
        assertThat(RetryPolicy.parseRetryAfter("Sun, 25 Oct 2026 07:00:00 GMT", clock)).contains(Duration.ZERO);
        assertThat(RetryPolicy.parseRetryAfter("soon", clock)).isEmpty();
        assertThat(RetryPolicy.parseRetryAfter("-5", clock)).isEmpty();
        assertThat(RetryPolicy.parseRetryAfter(null, clock)).isEmpty();
    }
}
