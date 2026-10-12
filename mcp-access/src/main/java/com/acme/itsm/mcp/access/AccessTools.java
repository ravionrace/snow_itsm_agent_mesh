package com.acme.itsm.mcp.access;

import com.acme.itsm.mcp.common.Args;
import com.acme.itsm.mcp.common.ServiceNowClient;
import com.acme.itsm.mcp.common.ToolExecutor;
import com.acme.itsm.mcp.common.ToolResponses;
import com.fasterxml.jackson.databind.JsonNode;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * Access MCP server. Answers "what may the signed-in user do?" from GET /api/<scope>/v1/access/me, called with
 * the user's own token, so the answer is ServiceNow's view of their group-granted roles. The agent runtime's
 * Access agent uses it to refuse requests and hide tools up front; the REST ACLs still enforce every call.
 */
@Component
public class AccessTools {

    private final ServiceNowClient sn;
    private final ToolExecutor exec;
    private final ToolResponses responses;

    public AccessTools(ServiceNowClient sn, ToolExecutor exec, ToolResponses responses) {
        this.sn = sn;
        this.exec = exec;
        this.responses = responses;
    }

    @McpTool(name = "get_my_permissions", description = """
            The signed-in user's read and write permissions for every ITSM API (incident, problem, change, \
            catalog, cmdb, kb), and the groups that grant them.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public CallToolResult getMyPermissions(McpTransportContext ctx) {
        return exec.run("get_my_permissions", ctx, c -> responses.ok(sn.get(c, "/me")));
    }

    @McpTool(name = "check_permission", description = """
            Whether the signed-in user may read or write one ITSM API. When the answer is no, the result says which \
            access to ask an administrator for.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public CallToolResult checkPermission(McpTransportContext ctx,
            @McpToolParam(description = "API id: incident | problem | change | catalog | cmdb | kb") String api,
            @McpToolParam(description = "read | write") String level) {
        return exec.run("check_permission", ctx, c -> {
            String apiId = Args.oneOf(api, "api", "incident", "problem", "change", "catalog", "cmdb", "kb");
            String lvl = Args.oneOf(level, "level", "read", "write");
            return responses.ok(AccessDecision.decide(sn.get(c, "/me"), apiId, lvl));
        });
    }

    /** Pure decision logic, unit-tested without ServiceNow. */
    static final class AccessDecision {
        private AccessDecision() {
        }

        static java.util.Map<String, Object> decide(JsonNode me, String api, String level) {
            for (JsonNode a : me.path("apis")) {
                if (!api.equals(a.path("api").asText())) {
                    continue;
                }
                boolean allowed = a.path(level).asBoolean(false);
                java.util.List<String> via = new java.util.ArrayList<>();
                a.path("granted_by").forEach(g -> via.add(g.path("group").asText() + " (" + g.path("level").asText() + ")"));
                String title = a.path("title").asText(api);
                return java.util.Map.of(
                        "api", api, "level", level, "allowed", allowed, "granted_by", via,
                        "message", allowed
                                ? "You have " + level + " access to the " + title + " API."
                                : "You do not have " + level + " access to the " + title + " API.",
                        "next_step", allowed ? "Proceed."
                                : "Ask your ServiceNow access admin to add you to a group with " + level
                                + " access to the " + title + " API.");
            }
            return java.util.Map.of("api", api, "level", level, "allowed", false, "granted_by", java.util.List.of(),
                    "message", "Unknown API " + api + ".", "next_step", "Use one of the listed API ids.");
        }
    }
}
