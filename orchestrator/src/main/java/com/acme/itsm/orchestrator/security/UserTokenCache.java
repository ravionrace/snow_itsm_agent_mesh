package com.acme.itsm.orchestrator.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Per-user ServiceNow tokens. A token is reused until {@code refreshMargin} before it expires, so a request
 * never starts with a token about to lapse. Concurrent requests for the same user share one in-flight mint
 * (single flight), failures are not cached, and the cache is bounded.
 */
public class UserTokenCache {

    public record Token(String value, Instant expiresAt) {
    }

    private final Map<String, CompletableFuture<Token>> entries = new ConcurrentHashMap<>();
    private final Duration refreshMargin;
    private final int maxEntries;
    private final Clock clock;

    public UserTokenCache(Duration refreshMargin, int maxEntries, Clock clock) {
        this.refreshMargin = refreshMargin;
        this.maxEntries = maxEntries;
        this.clock = clock;
    }

    public String get(String subject, Function<String, Token> mint) {
        CompletableFuture<Token> mine = new CompletableFuture<>();
        CompletableFuture<Token> future = entries.compute(subject, (key, current) ->
                current != null && (!current.isDone() || usable(current)) ? current : mine);
        if (future == mine) {
            // The HTTP call runs outside the map lock; concurrent callers for this user wait on the same future.
            try {
                mine.complete(mint.apply(subject));
            } catch (RuntimeException e) {
                mine.completeExceptionally(e);
            }
        }
        try {
            return future.join().value();
        } catch (CompletionException e) {
            entries.remove(subject, future);
            throw e.getCause() instanceof RuntimeException re ? re : e;
        } finally {
            if (entries.size() > maxEntries) {
                evict();
            }
        }
    }

    /** Drop a user's token, e.g. after ServiceNow rejected it. */
    public void invalidate(String subject) {
        entries.remove(subject);
    }

    public int size() {
        return entries.size();
    }

    private boolean usable(CompletableFuture<Token> f) {
        if (f.isCompletedExceptionally()) {
            return false;
        }
        Token t = f.getNow(null);
        return t != null && clock.instant().plus(refreshMargin).isBefore(t.expiresAt());
    }

    private void evict() {
        entries.entrySet().removeIf(e -> e.getValue().isDone() && !usable(e.getValue()));
        if (entries.size() > maxEntries) {
            entries.entrySet().stream()
                    .filter(e -> e.getValue().isDone() && !e.getValue().isCompletedExceptionally())
                    .sorted(Comparator.comparing(e -> e.getValue().join().expiresAt()))
                    .limit(entries.size() - maxEntries)
                    .map(Map.Entry::getKey)
                    .toList()
                    .forEach(entries::remove);
        }
    }
}
