package com.acme.itsm.orchestrator.channel;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class ChannelSignatureTest {

    /** Shared with the scoped app's test (tests/client/channel-signature.test.ts): both sides must sign identically. */
    static final String SECRET = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";
    static final String VECTOR = "fuI3gBx1HGUQrg2Wt3yjQvfBZaxxFVLWwgwag303AI8=";
    static final String TS = "1791300000";
    static final String NONCE = "nonce-0123456789abcdef";
    static final String PATH = "/channel/servicenow/v1/turns";
    static final String BODY = "{\"message\":\"VPN is down\"}";

    final ChannelSignature sig = new ChannelSignature(SECRET, Duration.ofMinutes(5),
            Clock.fixed(Instant.ofEpochSecond(1791300000L + 30), ZoneOffset.UTC));

    @Test
    void matchesTheSharedTestVector() {
        assertThat(sig.sign(ChannelSignature.canonical(TS, NONCE, "POST", PATH, "abel.tuter", BODY))).isEqualTo(VECTOR);
    }

    @Test
    void acceptsOnceThenRejectsReplay() {
        assertThat(sig.verify(TS, NONCE, "POST", PATH, "abel.tuter", BODY, VECTOR)).isNull();
        assertThat(sig.verify(TS, NONCE, "POST", PATH, "abel.tuter", BODY, VECTOR)).isEqualTo("replayed nonce");
    }

    @Test
    void rejectsTamperingAndStaleRequests() {
        assertThat(sig.verify(TS, NONCE, "POST", PATH, "admin", BODY, VECTOR)).isEqualTo("bad signature");          // other user
        assertThat(sig.verify(TS, NONCE, "POST", PATH, "abel.tuter", BODY.replace("down", "up"), VECTOR)).isEqualTo("bad signature");
        assertThat(sig.verify(TS, NONCE, "GET", PATH, "abel.tuter", BODY, VECTOR)).isEqualTo("bad signature");
        assertThat(sig.verify("1791299000", NONCE, "POST", PATH, "abel.tuter", BODY, VECTOR)).contains("stale");
        assertThat(sig.verify(TS, NONCE, "POST", PATH, "abel tuter;", BODY, VECTOR)).isEqualTo("malformed headers");
        assertThat(sig.verify(null, NONCE, "POST", PATH, "abel.tuter", BODY, VECTOR)).contains("missing");
    }
}
