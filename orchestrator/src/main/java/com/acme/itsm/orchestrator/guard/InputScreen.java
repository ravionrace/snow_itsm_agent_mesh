package com.acme.itsm.orchestrator.guard;

import com.acme.itsm.orchestrator.OrchestratorProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Layer-1 screening of the user's own message: size limits, control characters, and prompt-injection
 * attempts. Off-topic requests are turned away by the coordinator; tool results are screened separately by
 * the runtime because they are user-typed too.
 */
@Component
public class InputScreen {

    public record Verdict(boolean allowed, String text, String reason, int score) {
    }

    private record Signal(Pattern pattern, int weight) {
    }

    private static final List<Signal> SIGNALS = List.of(
            new Signal(Pattern.compile("(?i)ignore (all |any |the )?(previous|prior|above|earlier|your) (instructions|prompts?|rules)"), 3),
            new Signal(Pattern.compile("(?i)disregard (all |any |the )?(previous|prior|above|system|your)"), 3),
            new Signal(Pattern.compile("(?i)(reveal|print|show|repeat|tell me) (me )?(your|the) (system prompt|instructions|hidden prompt)"), 3),
            new Signal(Pattern.compile("(?i)\\byou are now\\b|\\bpretend (to be|you are)\\b|\\bjailbreak\\b|\\bDAN mode\\b"), 2),
            new Signal(Pattern.compile("(?i)</?\\s*(system|assistant|tool_result|instructions?)\\s*>"), 3),
            new Signal(Pattern.compile("(?i)\\bconfirmation_id\\b"), 2),
            new Signal(Pattern.compile("(?i)\\b(skip|bypass|without) (the )?(preview|confirmation|approval|cab)\\b"), 1),
            new Signal(Pattern.compile("(?i)\\bdeveloper mode\\b|\\bact as (an? )?(admin|administrator|root)\\b"), 2)
    );

    /** Score at which a message is refused outright. Lower scores pass; the downstream guards still apply. */
    static final int BLOCK_SCORE = 3;

    private final int maxChars;

    public InputScreen(OrchestratorProperties props) {
        this.maxChars = props.getLimits().getMaxMessageChars();
    }

    public Verdict screen(String raw) {
        if (raw == null || raw.isBlank()) {
            return new Verdict(false, "", "empty message", 0);
        }
        // Drop control and zero-width/bidi characters often used to hide instructions.
        String text = raw.replaceAll("[\\p{Cc}&&[^\\n\\t]]|[\\u200B-\\u200F\\u202A-\\u202E\\u2066-\\u2069\\uFEFF]", "").strip();
        if (text.length() > maxChars) {
            return new Verdict(false, text, "message is longer than " + maxChars + " characters", 0);
        }
        int score = 0;
        for (Signal s : SIGNALS) {
            if (s.pattern().matcher(text).find()) {
                score += s.weight();
            }
        }
        if (score >= BLOCK_SCORE) {
            return new Verdict(false, text, "the message looks like an attempt to change the assistant's instructions", score);
        }
        return new Verdict(true, text, null, score);
    }
}
