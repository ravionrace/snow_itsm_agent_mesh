package com.scruttlabs.snow.client.http;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** Removes credentials from headers and payloads before they are logged. */
public final class Redactor {

    private static final String REDACTED = "[REDACTED]";
    private static final Set<String> SENSITIVE_HEADERS = Set.of("authorization", "proxy-authorization", "cookie", "set-cookie");
    private static final Pattern FORM_SECRET = Pattern.compile(
            "(?i)(client_secret|client_id|access_token|refresh_token|password|username)=([^&]*)");
    private static final Pattern JSON_SECRET = Pattern.compile(
            "(?i)(\"(?:client_secret|client_id|access_token|refresh_token|password|username)\"\\s*:\\s*\")[^\"]*(\")");

    private Redactor() {
    }

    /** A copy of {@code headers} with credential headers replaced, sorted by name for readable logs. */
    public static Map<String, List<String>> headers(Map<String, List<String>> headers) {
        Map<String, List<String>> out = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.forEach((name, values) ->
                out.put(name, SENSITIVE_HEADERS.contains(name.toLowerCase(Locale.ROOT)) ? List.of(REDACTED) : values));
        return out;
    }

    /** {@code payload} with form and JSON credential fields replaced. */
    public static String payload(String payload) {
        if (payload == null || payload.isEmpty()) return "<empty>";
        String out = FORM_SECRET.matcher(payload).replaceAll("$1=" + REDACTED);
        return JSON_SECRET.matcher(out).replaceAll("$1" + REDACTED + "$2");
    }
}
