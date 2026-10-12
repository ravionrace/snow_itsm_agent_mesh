package com.acme.itsm.mcp.common;

/**
 * A tool failure that is reported back to the agent as data. Every error carries a {@code nextStep}
 * telling the agent what to do instead ("call lookup_group first"), so it can recover without guessing.
 */
public class ToolException extends RuntimeException {

    public enum Kind { ERROR, BLOCKED }

    private final Kind kind;
    private final String code;
    private final String nextStep;

    public ToolException(Kind kind, String code, String message, String nextStep) {
        super(message);
        this.kind = kind;
        this.code = code;
        this.nextStep = nextStep;
    }

    public static ToolException invalid(String message, String nextStep) {
        return new ToolException(Kind.ERROR, "INVALID_ARGUMENT", message, nextStep);
    }

    public static ToolException notFound(String what, String nextStep) {
        return new ToolException(Kind.ERROR, "NOT_FOUND", what + " was not found or is not visible to you.", nextStep);
    }

    /** A coded business rule refused the action. The agent must not try to work around it. */
    public static ToolException blocked(String rule, String message, String nextStep) {
        return new ToolException(Kind.BLOCKED, rule, message, nextStep);
    }

    public static ToolException unauthenticated() {
        return new ToolException(Kind.ERROR, "UNAUTHENTICATED", "No user identity was supplied with this tool call.",
                "Ask the user to sign in again. Do not retry.");
    }

    public Kind kind() {
        return kind;
    }

    public String code() {
        return code;
    }

    public String nextStep() {
        return nextStep;
    }
}
