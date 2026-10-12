package com.acme.itsm.mcp.problem;

import com.acme.itsm.mcp.common.ToolException;
import com.fasterxml.jackson.databind.JsonNode;

/** Coded business rules for problem records. */
final class ProblemRules {

    /** Problem state values in the default state model. */
    static final String RESOLVED = "106";
    static final String CLOSED = "107";

    private ProblemRules() {
    }

    static void checkOpen(JsonNode problem) {
        String state = problem.path("state_code").asText("");
        if (RESOLVED.equals(state) || CLOSED.equals(state)) {
            throw ToolException.blocked("PROBLEM_CLOSED",
                    problem.path("number").asText("The problem") + " is already " + problem.path("state").asText("closed") + ".",
                    "Tell the user the problem is closed; a new problem is needed if the issue has returned.");
        }
    }

    /** A known error needs a recorded root cause and a workaround, otherwise the KB article would be empty. */
    static void checkKnownErrorReady(JsonNode problem) {
        checkOpen(problem);
        if (problem.path("known_error").asBoolean(false)) {
            throw ToolException.blocked("ALREADY_KNOWN_ERROR",
                    problem.path("number").asText("The problem") + " is already a known error.",
                    "Tell the user; use search_known_errors to show the existing record.");
        }
        if (problem.path("cause_notes").asText("").isBlank()) {
            throw ToolException.blocked("ROOT_CAUSE_MISSING", "A root cause must be recorded first.",
                    "Call record_root_cause first, then try again.");
        }
        if (problem.path("workaround").asText("").isBlank()) {
            throw ToolException.blocked("WORKAROUND_MISSING", "A workaround must be recorded first.",
                    "Call add_workaround first, then try again.");
        }
    }
}
