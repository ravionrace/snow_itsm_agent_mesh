package com.acme.itsm.mcp.common;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Schema-level argument checks. The JSON schema on each tool already constrains types; these checks
 * enforce formats and enums in code, because a model can still send a well-typed but wrong value.
 */
public final class Args {

    public static final Pattern SYS_ID = Pattern.compile("^[0-9a-f]{32}$");
    public static final Pattern INCIDENT = Pattern.compile("^INC\\d{7,10}$");
    public static final Pattern PROBLEM = Pattern.compile("^PRB\\d{7,10}$");
    public static final Pattern CHANGE = Pattern.compile("^CHG\\d{7,10}$");
    public static final Pattern RITM = Pattern.compile("^RITM\\d{7,10}$");
    public static final Pattern KB = Pattern.compile("^KB\\d{7,10}$");
    public static final Pattern DATE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}( \\d{2}:\\d{2}:\\d{2})?$");

    private Args() {
    }

    public static String required(String value, String name, int maxLength) {
        if (value == null || value.isBlank()) {
            throw ToolException.invalid(name + " is required.", "Ask the user for " + name + ".");
        }
        String trimmed = value.strip();
        if (trimmed.length() > maxLength) {
            throw ToolException.invalid(name + " is longer than " + maxLength + " characters.",
                    "Shorten " + name + " and call again.");
        }
        return trimmed;
    }

    public static String optional(String value, String name, int maxLength) {
        return value == null || value.isBlank() ? null : required(value, name, maxLength);
    }

    /** A record number (INC0012345) or a 32-char sys_id. Returns it normalised. */
    public static String recordRef(String value, String name, Pattern numberPattern) {
        String v = required(value, name, 40);
        String upper = v.toUpperCase(Locale.ROOT);
        if (numberPattern.matcher(upper).matches()) {
            return upper;
        }
        String lower = v.toLowerCase(Locale.ROOT);
        if (SYS_ID.matcher(lower).matches()) {
            return lower;
        }
        throw ToolException.invalid(name + " '" + v + "' is not a valid record number or sys_id.",
                "Use the number exactly as shown (e.g. " + example(numberPattern) + ") or search for the record first.");
    }

    public static String sysId(String value, String name) {
        String v = required(value, name, 32).toLowerCase(Locale.ROOT);
        if (!SYS_ID.matcher(v).matches()) {
            throw ToolException.invalid(name + " must be a 32-character sys_id.",
                    "Look the record up first to get its sys_id.");
        }
        return v;
    }

    public static String optionalSysId(String value, String name) {
        return value == null || value.isBlank() ? null : sysId(value, name);
    }

    public static String oneOf(String value, String name, String... allowed) {
        String v = required(value, name, 60);
        for (String a : allowed) {
            if (a.equalsIgnoreCase(v)) {
                return a;
            }
        }
        throw ToolException.invalid(name + " must be one of " + Arrays.toString(allowed) + ".",
                "Pick one of the allowed values and call again.");
    }

    public static String optionalOneOf(String value, String name, String... allowed) {
        return value == null || value.isBlank() ? null : oneOf(value, name, allowed);
    }

    public static Integer range(Integer value, String name, int min, int max) {
        if (value == null) {
            return null;
        }
        if (value < min || value > max) {
            throw ToolException.invalid(name + " must be between " + min + " and " + max + ".",
                    "Use a value between " + min + " and " + max + ".");
        }
        return value;
    }

    public static String optionalDate(String value, String name) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (!DATE.matcher(value.strip()).matches()) {
            throw ToolException.invalid(name + " must be YYYY-MM-DD or YYYY-MM-DD HH:MM:SS (UTC).",
                    "Reformat the date and call again.");
        }
        return value.strip();
    }

    public static int limit(Integer value, int def, int max) {
        if (value == null) {
            return def;
        }
        return Math.max(1, Math.min(max, value));
    }

    public static boolean in(String value, Set<String> set) {
        return value != null && set.contains(value);
    }

    private static String example(Pattern p) {
        String s = p.pattern();
        int idx = s.indexOf('\\');
        return (idx > 1 ? s.substring(1, idx) : "") + "0012345";
    }
}
