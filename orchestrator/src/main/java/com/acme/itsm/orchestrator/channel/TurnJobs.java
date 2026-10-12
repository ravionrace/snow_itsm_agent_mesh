package com.acme.itsm.orchestrator.channel;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Background agent turns for the ServiceNow channel. ServiceNow REST transactions must stay short, so the instance
 * starts a turn, gets an id back at once, and polls. Bounded: a fixed pool and queue; results expire after a TTL.
 *
 * <p>Held in memory, so polling must reach the replica that started the turn (one replica, or sticky routing).
 */
public class TurnJobs {

    public enum Status { RUNNING, DONE, FAILED }

    public record Job(String id, String user, String sessionId, Status status, JsonNode result, int errorStatus,
                      String error, Instant updated) {
    }

    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private final ExecutorService pool;
    private final Duration ttl;
    private final Clock clock;

    public TurnJobs(int threads, int queue, Duration ttl, Clock clock) {
        this.pool = new ThreadPoolExecutor(threads, threads, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>(queue),
                r -> { Thread t = new Thread(r, "sn-turn"); t.setDaemon(true); return t; });
        this.ttl = ttl;
        this.clock = clock;
    }

    public Job submit(String user, String sessionId, Supplier<JsonNode> work) {
        purge();
        String id = UUID.randomUUID().toString().replace("-", "");
        Job running = new Job(id, user, sessionId, Status.RUNNING, null, 0, null, clock.instant());
        jobs.put(id, running);
        try {
            pool.execute(() -> {
                try {
                    jobs.put(id, new Job(id, user, sessionId, Status.DONE, work.get(), 0, null, clock.instant()));
                } catch (ResponseStatusException e) {
                    jobs.put(id, new Job(id, user, sessionId, Status.FAILED, null, e.getStatusCode().value(),
                            e.getReason(), clock.instant()));
                } catch (RuntimeException e) {
                    jobs.put(id, new Job(id, user, sessionId, Status.FAILED, null, 502,
                            "The assistant could not complete this request.", clock.instant()));
                }
            });
        } catch (RejectedExecutionException e) {
            jobs.remove(id);
            throw new ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                    "The assistant is busy. Please try again in a moment.");
        }
        return running;
    }

    /** The job, only for the user who started it. */
    public Job get(String id, String user) {
        Job job = jobs.get(id);
        return job != null && job.user().equals(user) ? job : null;
    }

    private void purge() {
        Instant cutoff = clock.instant().minus(ttl);
        jobs.values().removeIf(j -> j.status() != Status.RUNNING && j.updated().isBefore(cutoff));
    }

    public void shutdown() {
        pool.shutdownNow();
    }
}
