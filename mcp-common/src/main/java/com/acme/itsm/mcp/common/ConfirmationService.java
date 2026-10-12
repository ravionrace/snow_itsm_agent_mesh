package com.acme.itsm.mcp.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Two-step writes. A confirm tool called without a {@code confirmation_id} returns a preview and a
 * signed confirmation id; nothing is written. Only a second call with the same arguments and that id
 * (made by the orchestrator after the user clicks Confirm) commits the change.
 *
 * <p>The id is a stateless HMAC token binding user, tool, a hash of the canonical arguments and an
 * expiry, so any replica of this MCP server can verify it. Its nonce is also sent to ServiceNow as
 * the idempotency key, so a confirmed write runs at most once even across replicas.
 */
public class ConfirmationService {

    public static final String CONFIRMATION_ARG = "confirmation_id";

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();

    private final byte[] secret;
    private final Duration ttl;
    private final Clock clock;
    private final ObjectMapper canonical;
    private final SecureRandom random = new SecureRandom();
    /** Nonces already committed by this replica; ServiceNow idempotency covers the cross-replica case. */
    private final Map<String, Instant> used = new ConcurrentHashMap<>();
    private final ToolResponses responses;

    public ConfirmationService(String secret, Duration ttl, Clock clock, ToolResponses responses) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("mcp.confirmation.secret must be at least 32 characters");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.ttl = ttl;
        this.clock = clock;
        this.responses = responses;
        this.canonical = JsonMapper.builder().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).build();
    }

    /**
     * Run a confirm-type tool. Without a confirmation id this builds the preview; with one, it verifies
     * the id against the exact same arguments and then commits.
     *
     * @param args    the tool's business arguments, excluding {@code confirmation_id}
     * @param preview builds the preview (may read the current record)
     * @param commit  performs the write; receives the idempotency key to pass to ServiceNow
     */
    public String twoStep(CallerContext caller, String tool, Map<String, Object> args, String confirmationId,
                          Supplier<Preview> preview, Function<String, Object> commit) {
        String argsHash = hashArgs(args);
        if (confirmationId == null || confirmationId.isBlank()) {
            Instant expires = clock.instant().plus(ttl);
            String id = issue(caller.userId(), tool, argsHash, expires);
            return responses.pending(id, expires, preview.get());
        }
        String nonce = verify(confirmationId, caller.userId(), tool, argsHash);
        if (used.putIfAbsent(nonce, clock.instant().plus(ttl)) != null) {
            throw ToolException.blocked("CONFIRMATION_USED", "This confirmation has already been used.",
                    "Report the earlier result to the user. Do not repeat the write.");
        }
        purgeExpired();
        Object result = commit.apply("confirm-" + nonce);
        return responses.committed(result, confirmationId);
    }

    String issue(String user, String tool, String argsHash, Instant expires) {
        byte[] nonceBytes = new byte[12];
        random.nextBytes(nonceBytes);
        ObjectNode payload = canonical.createObjectNode();
        payload.put("u", user);
        payload.put("t", tool);
        payload.put("h", argsHash);
        payload.put("e", expires.getEpochSecond());
        payload.put("n", HexFormat.of().formatHex(nonceBytes));
        String body = B64.encodeToString(payload.toString().getBytes(StandardCharsets.UTF_8));
        return body + "." + B64.encodeToString(hmac(body));
    }

    /** @return the token's nonce when valid */
    String verify(String token, String user, String tool, String argsHash) {
        int dot = token.indexOf('.');
        if (dot <= 0) {
            throw invalidConfirmation();
        }
        String body = token.substring(0, dot);
        byte[] sig;
        try {
            sig = B64D.decode(token.substring(dot + 1));
        } catch (IllegalArgumentException e) {
            throw invalidConfirmation();
        }
        if (!MessageDigest.isEqual(sig, hmac(body))) {
            throw invalidConfirmation();
        }
        try {
            var payload = canonical.readTree(B64D.decode(body));
            if (!user.equals(payload.path("u").asText()) || !tool.equals(payload.path("t").asText())) {
                throw invalidConfirmation();
            }
            if (!argsHash.equals(payload.path("h").asText())) {
                throw ToolException.blocked("CONFIRMATION_MISMATCH",
                        "The arguments differ from the preview the user confirmed.",
                        "Call the tool again without confirmation_id to produce a fresh preview.");
            }
            if (clock.instant().getEpochSecond() > payload.path("e").asLong()) {
                throw ToolException.blocked("CONFIRMATION_EXPIRED", "The confirmation has expired.",
                        "Call the tool again without confirmation_id to produce a fresh preview.");
            }
            return payload.path("n").asText();
        } catch (java.io.IOException e) {
            throw invalidConfirmation();
        }
    }

    String hashArgs(Map<String, Object> args) {
        Map<String, Object> clean = new TreeMap<>();
        args.forEach((k, v) -> {
            if (v != null && !CONFIRMATION_ARG.equals(k)) {
                clean.put(k, v);
            }
        });
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.writeValueAsBytes(clean));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private byte[] hmac(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private void purgeExpired() {
        Instant now = clock.instant();
        used.values().removeIf(exp -> exp.isBefore(now));
    }

    private static ToolException invalidConfirmation() {
        return ToolException.blocked("CONFIRMATION_INVALID", "The confirmation id is not valid for this user and action.",
                "Only the user's Confirm click can supply a confirmation id. Produce a new preview instead.");
    }
}
