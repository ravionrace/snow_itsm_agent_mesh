package com.scruttlabs.snow.client.model;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/** Parses glide_date_time values as the ScopeGraph API sends them: {@code "2025-03-01 10:00:00 UTC"}. */
public final class SnowTime {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private SnowTime() {
    }

    /** The instant, or null for null or blank input. The " UTC" suffix is optional. */
    public static Instant parse(String value) {
        if (value == null || value.isBlank()) return null;
        String text = value.strip();
        if (text.endsWith(" UTC")) text = text.substring(0, text.length() - 4);
        try {
            return LocalDateTime.parse(text, FORMAT).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Not a glide_date_time: " + value, e);
        }
    }
}
