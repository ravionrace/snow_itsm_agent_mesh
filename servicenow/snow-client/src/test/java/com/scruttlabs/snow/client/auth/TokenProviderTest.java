package com.scruttlabs.snow.client.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(10)
class TokenProviderTest {

    private static final Duration SKEW = Duration.ofSeconds(60);
    private static final Duration LIFESPAN = Duration.ofMinutes(5);

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-25T10:00:00Z"));
    private final AtomicInteger fetches = new AtomicInteger();

    private AccessToken nextToken() {
        return new AccessToken("token-" + fetches.incrementAndGet(), clock.instant().plus(LIFESPAN));
    }

    @Test
    void validTokenIsReusedWithoutFetching() {
        TokenProvider provider = new TokenProvider(this::nextToken, clock, SKEW);

        AccessToken first = provider.current();
        for (int i = 0; i < 100; i++) assertThat(provider.current()).isSameAs(first);
        assertThat(fetches.get()).isEqualTo(1);
    }

    @Test
    void refreshesSkewBeforeExpiry() {
        TokenProvider provider = new TokenProvider(this::nextToken, clock, SKEW);
        assertThat(provider.current().value()).isEqualTo("token-1");

        clock.advance(LIFESPAN.minus(SKEW).minusSeconds(1));    // 3m59s: still outside the skew window
        assertThat(provider.current().value()).isEqualTo("token-1");

        clock.advance(Duration.ofSeconds(1));                    // 4m00s: expires within the skew
        assertThat(provider.current().value()).isEqualTo("token-2");
    }

    /** 50 threads find no token at the same moment: one fetch, and everyone gets that token. */
    @Test
    void concurrentCallersShareOneFetch() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        TokenProvider provider = new TokenProvider(() -> {
            entered.countDown();
            awaitQuietly(release);
            return nextToken();
        }, clock, SKEW);
        Queue<AccessToken> results = new ConcurrentLinkedQueue<>();

        List<Thread> threads = startAll(50, () -> results.add(provider.current()));
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        awaitAllWaiting(threads);
        release.countDown();
        for (Thread t : threads) t.join();

        assertThat(fetches.get()).isEqualTo(1);
        assertThat(results).hasSize(50).allSatisfy(t -> assertThat(t.value()).isEqualTo("token-1"));
    }

    @Test
    void invalidateDropsTheRejectedToken() {
        TokenProvider provider = new TokenProvider(this::nextToken, clock, SKEW);
        String rejected = provider.current().value();

        provider.invalidate(rejected);

        assertThat(provider.current().value()).isEqualTo("token-2");
    }

    /** Twenty requests failed with token-1; only the first invalidate counts, token-2 survives the rest. */
    @Test
    void staleInvalidateDoesNotDropTheReplacement() {
        TokenProvider provider = new TokenProvider(this::nextToken, clock, SKEW);
        String rejected = provider.current().value();

        provider.invalidate(rejected);
        AccessToken replacement = provider.current();
        for (int i = 0; i < 19; i++) provider.invalidate(rejected);

        assertThat(provider.current()).isSameAs(replacement);
        assertThat(fetches.get()).isEqualTo(2);
    }

    @Test
    void failedFetchIsNotCached() {
        AtomicInteger calls = new AtomicInteger();
        TokenProvider provider = new TokenProvider(() -> {
            if (calls.incrementAndGet() == 1) throw new IllegalStateException("instance down");
            return nextToken();
        }, clock, SKEW);

        assertThatThrownBy(provider::current).hasMessage("instance down");
        assertThat(provider.current().value()).isEqualTo("token-1");
    }

    @Test
    void failedFetchReachesEveryWaiter() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        TokenProvider provider = new TokenProvider(() -> {
            entered.countDown();
            awaitQuietly(release);
            throw new IllegalStateException("bad credentials");
        }, clock, SKEW);
        Queue<Throwable> failures = new ConcurrentLinkedQueue<>();

        List<Thread> threads = startAll(8, () -> {
            try {
                provider.current();
            } catch (IllegalStateException e) {
                failures.add(e);
            }
        });
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        awaitAllWaiting(threads);
        release.countDown();
        for (Thread t : threads) t.join();

        assertThat(failures).hasSize(8).allSatisfy(e -> assertThat(e).hasMessage("bad credentials"));
    }

    @Test
    void tokenValueIsNeverPrinted() {
        assertThat(new AccessToken("s3cr3t", Instant.EPOCH).toString()).doesNotContain("s3cr3t");
    }

    // ------------------------------------------------------------------------------------------------ helpers

    static final class MutableClock extends Clock {
        private volatile Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    private static List<Thread> startAll(int count, Runnable task) {
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Thread t = new Thread(task, "token-" + i);
            threads.add(t);
            t.start();
        }
        return threads;
    }

    /** Waits until every thread is parked (in the fetcher's latch or on the shared future). */
    private static void awaitAllWaiting(List<Thread> threads) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!threads.stream().allMatch(t -> t.getState() == Thread.State.WAITING)) {
            if (System.nanoTime() > deadline) throw new AssertionError("threads never parked");
            Thread.sleep(5);
        }
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
