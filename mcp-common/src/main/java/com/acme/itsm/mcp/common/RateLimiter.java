package com.acme.itsm.mcp.common;

import java.time.Duration;

/**
 * Token bucket guarding this server's calls to the ServiceNow instance.
 */
public class RateLimiter {

    private final double permitsPerNano;
    private final double capacity;
    private final long maxWaitNanos;
    private double tokens;
    private long lastRefill;

    public RateLimiter(double permitsPerSecond, int burst, Duration maxWait) {
        this.permitsPerNano = permitsPerSecond / 1_000_000_000d;
        this.capacity = burst;
        this.maxWaitNanos = maxWait.toNanos();
        this.tokens = burst;
        this.lastRefill = System.nanoTime();
    }

    public void acquire() {
        long waitNanos;
        synchronized (this) {
            refill();
            if (tokens >= 1) {
                tokens -= 1;
                return;
            }
            waitNanos = (long) Math.ceil((1 - tokens) / permitsPerNano);
            if (waitNanos > maxWaitNanos) {
                throw new ToolException(ToolException.Kind.ERROR, "RATE_LIMITED",
                        "Too many requests to ServiceNow right now.",
                        "Tell the user the system is busy and to try again in a moment. Do not loop.");
            }
            // Reserve the token now so concurrent callers queue behind us.
            tokens -= 1;
        }
        try {
            Thread.sleep(waitNanos / 1_000_000, (int) (waitNanos % 1_000_000));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private void refill() {
        long now = System.nanoTime();
        tokens = Math.min(capacity, tokens + (now - lastRefill) * permitsPerNano);
        lastRefill = now;
    }
}
