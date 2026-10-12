package com.acme.itsm.mcp.incident;

import com.acme.itsm.mcp.common.ToolException;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Coded business rules. These run in the MCP server regardless of what the model was told or tricked into;
 * the scoped app enforces its own ACLs and business rules behind them.
 */
final class IncidentRules {

    private IncidentRules() {
    }

    /** No P1 resolve: critical incidents are closed by the major incident manager, not the agent. */
    static void checkResolvable(JsonNode incident) {
        String priority = incident.path("priority_code").asText("");
        if ("1".equals(priority)) {
            throw ToolException.blocked("P1_RESOLVE",
                    incident.path("number").asText("This incident") + " is a P1 (critical) incident and cannot be resolved by the assistant.",
                    "Tell the user P1 incidents must be resolved manually by the major incident manager in ServiceNow.");
        }
        String state = incident.path("state_code").asText("");
        if ("6".equals(state) || "7".equals(state) || "8".equals(state)) {
            throw ToolException.blocked("ALREADY_CLOSED",
                    incident.path("number").asText("This incident") + " is already " + incident.path("state").asText("closed") + ".",
                    "Tell the user the incident is already resolved or closed.");
        }
    }

    /** Reassign only within the user's own groups. */
    static void checkGroupMembership(JsonNode myGroups, String groupSysId) {
        if (myGroups != null && myGroups.isArray()) {
            for (JsonNode g : myGroups) {
                if (groupSysId.equals(g.path("sys_id").asText())) {
                    return;
                }
            }
        }
        throw ToolException.blocked("GROUP_NOT_MEMBER",
                "You can only assign incidents to groups you are a member of.",
                "Tell the user to ask a member of that group, or their manager, to take the incident.");
    }
}
