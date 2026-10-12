package com.acme.itsm.mcp.change;

import com.acme.itsm.mcp.common.ToolException;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/**
 * Coded business rules for changes. Emergency changes never go through the assistant, and state moves
 * follow the change model one step at a time.
 */
final class ChangeRules {

    /** change_request.state values in the default change model. */
    static final String NEW = "-5";
    static final String SCHEDULED = "-2";
    static final String IMPLEMENT = "-1";
    static final String REVIEW = "0";

    private ChangeRules() {
    }

    static void rejectEmergency(String type) {
        if (type != null && type.strip().equalsIgnoreCase("emergency")) {
            throw ToolException.blocked("NO_EMERGENCY_CHANGE",
                    "The assistant cannot raise emergency changes.",
                    "Tell the user to raise the emergency change directly in ServiceNow and contact the ECAB / change manager.");
        }
    }

    static void checkCreate(Map<String, Object> body) {
        if ("standard".equals(body.get("type")) && body.get("template") == null) {
            throw ToolException.invalid("A standard change needs a template.",
                    "Call get_std_templates and pass the chosen template sys_id.");
        }
        Object start = body.get("start_date");
        Object end = body.get("end_date");
        if (start != null && end != null && start.toString().compareTo(end.toString()) >= 0) {
            throw ToolException.invalid("end_date must be after start_date.", "Fix the planned window.");
        }
    }

    static void checkReadyForApproval(JsonNode change) {
        rejectEmergency(change.path("type").asText(null));
        if (!NEW.equals(change.path("state_code").asText())) {
            throw ToolException.blocked("NOT_NEW", change.path("number").asText("The change") + " is in state "
                            + change.path("state").asText("?") + ", not New.",
                    "Tell the user the change has already been submitted.");
        }
        if (change.path("start_date").asText("").isBlank() || change.path("end_date").asText("").isBlank()) {
            throw ToolException.invalid("The change has no planned window.",
                    "Ask the user for planned start and end, then update the change in ServiceNow.");
        }
        if ("normal".equalsIgnoreCase(change.path("type").asText())) {
            for (String f : new String[]{"implementation_plan", "backout_plan", "test_plan"}) {
                if (change.path(f).asText("").isBlank()) {
                    throw ToolException.invalid("The change is missing its " + f.replace('_', ' ') + ".",
                            "Ask the user for the " + f.replace('_', ' ') + ".");
                }
            }
        }
    }

    static void checkTransition(JsonNode change, Map<String, Object> body) {
        rejectEmergency(change.path("type").asText(null));
        String from = change.path("state_code").asText();
        String to = (String) body.get("state");
        boolean ok = switch (to) {
            case "implement" -> SCHEDULED.equals(from);
            case "review" -> IMPLEMENT.equals(from);
            case "closed" -> REVIEW.equals(from);
            default -> false;
        };
        if (!ok) {
            throw ToolException.blocked("INVALID_TRANSITION",
                    "Cannot move " + change.path("number").asText("the change") + " from "
                            + change.path("state").asText(from) + " to " + to + ".",
                    "Tell the user the current state; changes move scheduled -> implement -> review -> closed.");
        }
        if ("closed".equals(to) && (body.get("close_code") == null || body.get("close_notes") == null)) {
            throw ToolException.invalid("Closing a change needs close_code and close_notes.",
                    "Ask the user how the change went.");
        }
    }
}
