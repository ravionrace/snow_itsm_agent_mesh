package com.acme.itsm.mcp.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfirmationServiceTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-123";
    private final ObjectMapper mapper = new ObjectMapper();
    private final ToolResponses responses = new ToolResponses(mapper);
    private final CallerContext alice = new CallerContext("tok", "alice", "r1", "s1", null);
    private final CallerContext bob = new CallerContext("tok2", "bob", "r2", "s2", null);

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-04T12:00:00Z"));
    private final ConfirmationService service = new ConfirmationService(SECRET, Duration.ofMinutes(10), clock, responses);

    private Map<String, Object> args() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("number", "INC0012345");
        args.put("close_code", "Solution provided");
        args.put("close_notes", "Patched");
        return args;
    }

    @Test
    void previewDoesNotCommit() throws Exception {
        AtomicInteger commits = new AtomicInteger();
        String out = service.twoStep(alice, "resolve_incident", args(), null,
                () -> Preview.of("Resolve", "incident", "INC0012345").build(), k -> commits.incrementAndGet());
        JsonNode json = mapper.readTree(out);
        assertThat(json.path("status").asText()).isEqualTo("PENDING_CONFIRMATION");
        assertThat(json.path("confirmation_id").asText()).isNotBlank();
        assertThat(commits).hasValue(0);
    }

    @Test
    void confirmCommitsOnceWithIdempotencyKey() throws Exception {
        String id = preview(alice, args());
        AtomicReference<String> key = new AtomicReference<>();
        String out = service.twoStep(alice, "resolve_incident", args(), id, () -> null, k -> {
            key.set(k);
            return Map.of("number", "INC0012345", "state", "Resolved");
        });
        assertThat(mapper.readTree(out).path("committed").asBoolean()).isTrue();
        assertThat(key.get()).startsWith("confirm-");

        assertThatThrownBy(() -> service.twoStep(alice, "resolve_incident", args(), id, () -> null, k -> "again"))
                .isInstanceOf(ToolException.class).extracting("code").isEqualTo("CONFIRMATION_USED");
    }

    @Test
    void changedArgumentsAreRejected() {
        String id = preview(alice, args());
        Map<String, Object> tampered = args();
        tampered.put("close_notes", "something else");
        assertThatThrownBy(() -> service.twoStep(alice, "resolve_incident", tampered, id, () -> null, k -> "x"))
                .isInstanceOf(ToolException.class).extracting("code").isEqualTo("CONFIRMATION_MISMATCH");
    }

    @Test
    void otherUserOrToolIsRejected() {
        String id = preview(alice, args());
        assertThatThrownBy(() -> service.twoStep(bob, "resolve_incident", args(), id, () -> null, k -> "x"))
                .isInstanceOf(ToolException.class).extracting("code").isEqualTo("CONFIRMATION_INVALID");
        assertThatThrownBy(() -> service.twoStep(alice, "update_incident", args(), id, () -> null, k -> "x"))
                .isInstanceOf(ToolException.class).extracting("code").isEqualTo("CONFIRMATION_INVALID");
    }

    @Test
    void forgedOrExpiredIsRejected() {
        String id = preview(alice, args());
        String forged = id.substring(0, id.indexOf('.') + 1) + "AAAA";
        assertThatThrownBy(() -> service.twoStep(alice, "resolve_incident", args(), forged, () -> null, k -> "x"))
                .isInstanceOf(ToolException.class).extracting("code").isEqualTo("CONFIRMATION_INVALID");

        clock.advance(Duration.ofMinutes(11));
        assertThatThrownBy(() -> service.twoStep(alice, "resolve_incident", args(), id, () -> null, k -> "x"))
                .isInstanceOf(ToolException.class).extracting("code").isEqualTo("CONFIRMATION_EXPIRED");
    }

    @Test
    void argumentOrderDoesNotMatter() {
        Map<String, Object> reordered = new LinkedHashMap<>();
        reordered.put("close_notes", "Patched");
        reordered.put("number", "INC0012345");
        reordered.put("close_code", "Solution provided");
        reordered.put("ignored_null", null);
        assertThat(service.hashArgs(reordered)).isEqualTo(service.hashArgs(args()));
    }

    private String preview(CallerContext who, Map<String, Object> args) {
        try {
            return mapper.readTree(service.twoStep(who, "resolve_incident", args, null,
                    () -> Preview.of("Resolve", "incident", "INC0012345").build(), k -> null))
                    .path("confirmation_id").asText();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
