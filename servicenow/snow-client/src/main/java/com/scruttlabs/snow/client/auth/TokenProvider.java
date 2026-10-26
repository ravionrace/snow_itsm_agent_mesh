package com.scruttlabs.snow.client.auth;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Thread-safe cache for one access token.
 *
 * <ul>
 *   <li><b>Fast path:</b> a valid token is returned with one volatile read, no lock.</li>
 *   <li><b>Single flight:</b> when the token is missing or about to expire, the first caller fetches a new one
 *       and every caller that arrives meanwhile waits for that fetch instead of starting its own.</li>
 *   <li><b>Early refresh:</b> a token counts as expired {@code refreshSkew} before its real expiry, so a request
 *       never leaves with a token that dies on the way.</li>
 *   <li><b>Compare-and-invalidate:</b> after a 401, {@link #invalidate} drops the token only if it is still the
 *       one that was rejected. When twenty requests fail with the same token, the first drops it, one fetch
 *       replaces it, and the other nineteen do not throw the new token away.</li>
 *   <li><b>Failures are not cached:</b> a failed fetch reaches every waiting caller, and the next call tries
 *       again.</li>
 * </ul>
 */
public final class TokenProvider {

    private final Supplier<AccessToken> fetcher;
    private final Clock clock;
    private final Duration refreshSkew;

    private final AtomicReference<AccessToken> current = new AtomicReference<>();
    /** The fetch in progress, or null. Whoever installs a future here runs the fetch. */
    private final AtomicReference<CompletableFuture<AccessToken>> inFlight = new AtomicReference<>();

    public TokenProvider(Supplier<AccessToken> fetcher, Clock clock, Duration refreshSkew) {
        this.fetcher = Objects.requireNonNull(fetcher, "fetcher");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.refreshSkew = Objects.requireNonNull(refreshSkew, "refreshSkew");
        if (refreshSkew.isNegative()) throw new IllegalArgumentException("refreshSkew must not be negative");
    }

    /** A token that is valid for at least {@code refreshSkew}; fetches one when needed. */
    public AccessToken current() {
        AccessToken token = current.get();
        if (isUsable(token)) return token;
        return refresh();
    }

    /** Drops the token whose value is {@code rejectedValue}, unless it has already been replaced. */
    public void invalidate(String rejectedValue) {
        AccessToken token = current.get();
        if (token != null && token.value().equals(rejectedValue)) {
            current.compareAndSet(token, null);
        }
    }

    private AccessToken refresh() {
        CompletableFuture<AccessToken> mine = new CompletableFuture<>();
        CompletableFuture<AccessToken> running = inFlight.compareAndExchange(null, mine);
        if (running != null) return await(running);
        try {
            // Another thread may have finished a fetch between our fast-path read and winning the race.
            AccessToken token = current.get();
            if (!isUsable(token)) {
                token = Objects.requireNonNull(fetcher.get(), "token fetcher returned null");
                current.set(token);
            }
            mine.complete(token);
            return token;
        } catch (Throwable t) {
            mine.completeExceptionally(t);
            throw t;
        } finally {
            inFlight.compareAndSet(mine, null);
        }
    }

    private boolean isUsable(AccessToken token) {
        return token != null && !token.isExpiring(clock, refreshSkew);
    }

    private static AccessToken await(CompletableFuture<AccessToken> fetch) {
        try {
            return fetch.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for an access token", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("Access token fetch failed", cause);
        } catch (CancellationException e) {
            throw new IllegalStateException("Access token fetch was cancelled", e);
        }
    }
}
