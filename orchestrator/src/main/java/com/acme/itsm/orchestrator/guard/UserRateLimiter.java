package com.acme.itsm.orchestrator.guard;

import com.acme.itsm.orchestrator.OrchestratorProperties;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-user request limit at the API. The WAF / API gateway applies coarse limits in front; this one keeps a
 * single user from driving up model spend. Per-instance; use the gateway's limiter when running many replicas.
 */
@Component
public class UserRateLimiter {

    private final int perMinute;
    private final Map<String, long[]> windows = new ConcurrentHashMap<>();

    public UserRateLimiter(OrchestratorProperties props) {
        this.perMinute = props.getLimits().getRequestsPerMinute();
    }

    /** @return true if the request may proceed */
    public boolean tryAcquire(String userId) {
        long minute = System.currentTimeMillis() / 60_000;
        long[] w = windows.computeIfAbsent(userId, k -> new long[]{minute, 0});
        synchronized (w) {
            if (w[0] != minute) {
                w[0] = minute;
                w[1] = 0;
            }
            if (w[1] >= perMinute) {
                return false;
            }
            w[1]++;
            return true;
        }
    }
}
