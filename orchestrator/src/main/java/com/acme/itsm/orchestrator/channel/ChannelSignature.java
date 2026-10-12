package com.acme.itsm.orchestrator.channel;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Verifies requests signed by the ServiceNow instance:
 * <pre>
 *   X-Mesh-Timestamp  epoch seconds          X-Mesh-Nonce  random, single use
 *   X-Mesh-User       sys_user.user_name     X-Mesh-Signature  base64(HMAC-SHA256(secret, canonical))
 *   canonical = timestamp \n nonce \n METHOD \n path \n user \n body
 * </pre>
 * The same canonical string is built by the scoped app (src/server/services/chat-proxy-service.ts).
 */
public class ChannelSignature {

    private final byte[] key;
    private final Duration maxSkew;
    private final Clock clock;
    private final Map<String, Instant> seenNonces = new ConcurrentHashMap<>();

    public ChannelSignature(String base64Secret, Duration maxSkew, Clock clock) {
        if (base64Secret == null || base64Secret.isBlank()) {
            throw new IllegalStateException("itsm.channel.secret(-file) is required when the ServiceNow channel is enabled");
        }
        this.key = Base64.getDecoder().decode(base64Secret.strip());
        if (key.length < 32) {
            throw new IllegalStateException("The ServiceNow channel secret must be at least 32 bytes (base64 encoded)");
        }
        this.maxSkew = maxSkew;
        this.clock = clock;
    }

    public static String canonical(String ts, String nonce, String method, String path, String user, String body) {
        return ts + "\n" + nonce + "\n" + method + "\n" + path + "\n" + user + "\n" + body;
    }

    public String sign(String canonical) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** @return null when valid, otherwise why it was rejected (never echoed to the caller in detail) */
    public String verify(String ts, String nonce, String method, String path, String user, String body, String signature) {
        if (ts == null || nonce == null || user == null || signature == null) {
            return "missing signature headers";
        }
        if (!user.matches("^[A-Za-z0-9._@+-]{1,100}$") || nonce.length() < 16 || nonce.length() > 100) {
            return "malformed headers";
        }
        long epoch;
        try {
            epoch = Long.parseLong(ts);
        } catch (NumberFormatException e) {
            return "malformed timestamp";
        }
        Instant now = clock.instant();
        if (Math.abs(now.getEpochSecond() - epoch) > maxSkew.toSeconds()) {
            return "stale or future timestamp";
        }
        byte[] expected = sign(canonical(ts, nonce, method, path, user, body)).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, signature.getBytes(StandardCharsets.UTF_8))) {
            return "bad signature";
        }
        seenNonces.values().removeIf(t -> t.isBefore(now.minus(maxSkew.multipliedBy(2))));
        if (seenNonces.putIfAbsent(nonce, now) != null) {
            return "replayed nonce";
        }
        return null;
    }
}
