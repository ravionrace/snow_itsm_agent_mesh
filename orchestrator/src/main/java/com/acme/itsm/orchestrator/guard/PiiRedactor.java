package com.acme.itsm.orchestrator.guard;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Redacts PII before any prompt reaches an LLM.
 *
 * <ul>
 *   <li>Contact data the agents may legitimately need (emails, phone numbers, IP addresses) becomes a stable
 *       placeholder such as {@code [EMAIL_1]}. The placeholder-to-value vault is stored with the session and
 *       sent to the runtime per request so tool arguments can be rehydrated just before the ServiceNow call;
 *       the model only ever sees the placeholder.</li>
 *   <li>Secrets and high-risk identifiers (card numbers, national ids, passwords) are removed irreversibly.</li>
 * </ul>
 */
@Component
public class PiiRedactor {

    record Rule(String type, Pattern pattern, boolean reversible) {
    }

    private static final List<Rule> RULES = List.of(
            new Rule("KEY", Pattern.compile("-----BEGIN [A-Z ]*PRIVATE KEY-----[\\s\\S]*?-----END [A-Z ]*PRIVATE KEY-----"), false),
            new Rule("SECRET", Pattern.compile("(?i)\\b(?:password|passwd|pwd|passcode|api[_-]?key|secret|token)\\b\\s*(?:is|:|=)\\s*\\S+"), false),
            new Rule("CARD", Pattern.compile("\\b(?:\\d[ -]?){13,19}\\b"), false),
            new Rule("SSN", Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b"), false),
            new Rule("IBAN", Pattern.compile("\\b[A-Z]{2}\\d{2}[A-Z0-9]{11,30}\\b"), false),
            new Rule("EMAIL", Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b"), true),
            new Rule("PHONE", Pattern.compile("(?<![\\w-])\\+?\\d{1,3}?[ .-]?\\(?\\d{3}\\)?[ .-]?\\d{3}[ .-]?\\d{4}(?![\\w-])"), true),
            new Rule("IP", Pattern.compile("\\b(?:(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)\\b"), true)
    );

    public record Result(String text, Map<String, String> vault, List<String> removed) {
    }

    /**
     * @param vault the session's existing placeholder vault; the same value keeps the same placeholder
     */
    public Result redact(String input, Map<String, String> vault) {
        Map<String, String> out = new LinkedHashMap<>(vault);
        List<String> removed = new java.util.ArrayList<>();
        String text = input;
        for (Rule rule : RULES) {
            Matcher m = rule.pattern().matcher(text);
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                String replacement;
                if (rule.reversible()) {
                    replacement = placeholderFor(rule.type(), m.group(), out);
                } else {
                    replacement = "[REDACTED_" + rule.type() + "]";
                    removed.add(rule.type());
                }
                m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            }
            m.appendTail(sb);
            text = sb.toString();
        }
        return new Result(text, out, removed);
    }

    /** Put real values back for display to the same user. */
    public String rehydrate(String text, Map<String, String> vault) {
        if (text == null || vault.isEmpty()) {
            return text;
        }
        String out = text;
        for (Map.Entry<String, String> e : vault.entrySet()) {
            out = out.replace(e.getKey(), e.getValue());
        }
        return out;
    }

    private static String placeholderFor(String type, String value, Map<String, String> vault) {
        for (Map.Entry<String, String> e : vault.entrySet()) {
            if (e.getValue().equals(value) && e.getKey().startsWith("[" + type + "_")) {
                return e.getKey();
            }
        }
        long n = vault.keySet().stream().filter(k -> k.startsWith("[" + type + "_")).count() + 1;
        String key = "[" + type + "_" + n + "]";
        vault.put(key, value);
        return key;
    }
}
