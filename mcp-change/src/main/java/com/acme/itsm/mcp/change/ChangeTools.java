package com.acme.itsm.mcp.change;

import com.acme.itsm.mcp.common.Args;
import com.acme.itsm.mcp.common.ConfirmationService;
import com.acme.itsm.mcp.common.Preview;
import com.acme.itsm.mcp.common.ServiceNowClient;
import com.acme.itsm.mcp.common.ToolExecutor;
import com.acme.itsm.mcp.common.ToolResponses;
import com.fasterxml.jackson.databind.JsonNode;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Change MCP server. Calls /api/x_1320561_itsm_ai/v1/change, which uses the ChangeRequest script API.
 */
@Component
public class ChangeTools {

    static final String CONFIRM_HELP = "Leave empty. Set only by the platform after the user clicks Confirm.";

    private final ServiceNowClient sn;
    private final ConfirmationService confirmations;
    private final ToolExecutor exec;
    private final ToolResponses responses;

    public ChangeTools(ServiceNowClient sn, ConfirmationService confirmations, ToolExecutor exec, ToolResponses responses) {
        this.sn = sn;
        this.confirmations = confirmations;
        this.exec = exec;
        this.responses = responses;
    }

    @McpTool(name = "get_std_templates", description = """
            List active standard change templates (pre-approved changes), optionally filtered by text. Prefer a \
            standard change whenever a template fits.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public CallToolResult getStdTemplates(McpTransportContext ctx,
            @McpToolParam(description = "Words to match in the template name or description", required = false) String text,
            @McpToolParam(description = "Max results, 1-25 (default 10)", required = false) Integer limit) {
        return exec.run("get_std_templates", ctx, c -> {
            Map<String, Object> q = new LinkedHashMap<>();
            q.put("text", Args.optional(text, "text", 200));
            q.put("limit", Args.limit(limit, 10, 25));
            return responses.ok(sn.get(c, "/templates", q));
        });
    }

    @McpTool(name = "create_change", description = """
            Create a normal or standard change. Emergency changes are never raised by the assistant. A standard \
            change needs a template sys_id from get_std_templates. Planned dates are UTC. Returns a preview first.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false))
    public CallToolResult createChange(McpTransportContext ctx,
            @McpToolParam(description = "normal | standard") String type,
            @McpToolParam(description = "One-line summary") String short_description,
            @McpToolParam(description = "Standard change template sys_id (required for standard)", required = false) String template,
            @McpToolParam(description = "Detailed description", required = false) String description,
            @McpToolParam(description = "Primary CI sys_id", required = false) String cmdb_ci,
            @McpToolParam(description = "Planned start, YYYY-MM-DD HH:MM:SS UTC", required = false) String start_date,
            @McpToolParam(description = "Planned end, YYYY-MM-DD HH:MM:SS UTC", required = false) String end_date,
            @McpToolParam(description = "Business justification (normal changes)", required = false) String justification,
            @McpToolParam(description = "Implementation plan (normal changes)", required = false) String implementation_plan,
            @McpToolParam(description = "Backout plan (normal changes)", required = false) String backout_plan,
            @McpToolParam(description = "Test plan (normal changes)", required = false) String test_plan,
            @McpToolParam(description = "Related incident or problem number that this change fixes", required = false) String related_record,
            @McpToolParam(description = CONFIRM_HELP, required = false) String confirmation_id) {
        return exec.run("create_change", ctx, c -> {
            ChangeRules.rejectEmergency(type);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("type", Args.oneOf(type, "type", "normal", "standard"));
            body.put("short_description", Args.required(short_description, "short_description", 160));
            body.put("template", Args.optionalSysId(template, "template"));
            body.put("description", Args.optional(description, "description", 4000));
            body.put("cmdb_ci", Args.optionalSysId(cmdb_ci, "cmdb_ci"));
            body.put("start_date", Args.optionalDate(start_date, "start_date"));
            body.put("end_date", Args.optionalDate(end_date, "end_date"));
            body.put("justification", Args.optional(justification, "justification", 4000));
            body.put("implementation_plan", Args.optional(implementation_plan, "implementation_plan", 8000));
            body.put("backout_plan", Args.optional(backout_plan, "backout_plan", 8000));
            body.put("test_plan", Args.optional(test_plan, "test_plan", 8000));
            body.put("related_record", related_record == null || related_record.isBlank() ? null
                    : Args.required(related_record, "related_record", 20).toUpperCase());
            ChangeRules.checkCreate(body);
            return confirmations.twoStep(c, "create_change", body, confirmation_id,
                    () -> {
                        Preview.Builder p = Preview.of("Create " + body.get("type") + " change", "change_request", "(new)");
                        body.forEach((k, v) -> p.set(k, v == null ? null : String.valueOf(v)));
                        return p.build();
                    },
                    key -> sn.post(c, "", body, key));
        });
    }

    @McpTool(name = "get_change", description = """
            Fetch a change by CHG number or sys_id, with its state, risk, planned window, change tasks and approvals.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public CallToolResult getChange(McpTransportContext ctx,
            @McpToolParam(description = "Change number or sys_id") String number) {
        return exec.run("get_change", ctx, c -> responses.ok(sn.get(c, "/" + Args.recordRef(number, "number", Args.CHANGE))));
    }

    @McpTool(name = "assess_risk", description = """
            Run the change risk assessment / risk calculator for a change and return the calculated risk and impact \
            with the conditions that drove it. Does not submit the change.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public CallToolResult assessRisk(McpTransportContext ctx,
            @McpToolParam(description = "Change number or sys_id") String number) {
        return exec.run("assess_risk", ctx, c -> responses.ok(sn.get(c, "/" + Args.recordRef(number, "number", Args.CHANGE) + "/risk")));
    }

    @McpTool(name = "check_conflicts", description = """
            Check a change's planned window against blackout windows, maintenance schedules and other changes on \
            the same CI. Run it before request_approval.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public CallToolResult checkConflicts(McpTransportContext ctx,
            @McpToolParam(description = "Change number or sys_id") String number) {
        return exec.run("check_conflicts", ctx, c -> responses.ok(sn.get(c, "/" + Args.recordRef(number, "number", Args.CHANGE) + "/conflicts")));
    }

    @McpTool(name = "get_cab_schedule", description = """
            List upcoming CAB meetings and their agendas, to tell the user when a normal change can be reviewed.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public CallToolResult getCabSchedule(McpTransportContext ctx,
            @McpToolParam(description = "Days ahead to look, 1-60 (default 14)", required = false) Integer days) {
        return exec.run("get_cab_schedule", ctx, c ->
                responses.ok(sn.get(c, "/cab_schedule", Map.of("days", Args.range(days == null ? 14 : days, "days", 1, 60)))));
    }

    @McpTool(name = "request_approval", description = """
            Submit a New change for approval (moves it to Assess and triggers the approval workflow). The change must \
            have a planned window, and for normal changes implementation, backout and test plans. Returns a preview \
            that includes any schedule conflicts.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false))
    public CallToolResult requestApproval(McpTransportContext ctx,
            @McpToolParam(description = "Change number or sys_id") String number,
            @McpToolParam(description = CONFIRM_HELP, required = false) String confirmation_id) {
        return exec.run("request_approval", ctx, c -> {
            String ref = Args.recordRef(number, "number", Args.CHANGE);
            JsonNode cur = sn.get(c, "/" + ref);
            ChangeRules.checkReadyForApproval(cur);
            Map<String, Object> body = Map.of("number", ref);
            return confirmations.twoStep(c, "request_approval", body, confirmation_id,
                    () -> {
                        JsonNode conflicts = sn.get(c, "/" + ref + "/conflicts");
                        Preview.Builder p = Preview.of("Request approval", "change_request", cur.path("number").asText(ref))
                                .change("state", cur.path("state").asText(null), "Assess");
                        int n = conflicts.path("conflicts").size();
                        if (n > 0) {
                            p.warning(n + " schedule conflict(s) found; approvers will see them.");
                        }
                        return p.build();
                    },
                    key -> sn.post(c, "/" + ref + "/request_approval", Map.of(), key));
        });
    }

    @McpTool(name = "move_to_state", description = """
            Move an approved change forward: scheduled -> implement, implement -> review, review -> closed. Closing \
            needs a close code and notes. Returns a preview first.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = true))
    public CallToolResult moveToState(McpTransportContext ctx,
            @McpToolParam(description = "Change number or sys_id") String number,
            @McpToolParam(description = "implement | review | closed") String state,
            @McpToolParam(description = "Close code when closing: successful | successful_issues | unsuccessful", required = false) String close_code,
            @McpToolParam(description = "Close notes when closing", required = false) String close_notes,
            @McpToolParam(description = CONFIRM_HELP, required = false) String confirmation_id) {
        return exec.run("move_to_state", ctx, c -> {
            String ref = Args.recordRef(number, "number", Args.CHANGE);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("number", ref);
            body.put("state", Args.oneOf(state, "state", "implement", "review", "closed"));
            body.put("close_code", Args.optionalOneOf(close_code, "close_code", "successful", "successful_issues", "unsuccessful"));
            body.put("close_notes", Args.optional(close_notes, "close_notes", 4000));
            JsonNode cur = sn.get(c, "/" + ref);
            ChangeRules.checkTransition(cur, body);
            return confirmations.twoStep(c, "move_to_state", body, confirmation_id,
                    () -> Preview.of("Move change to " + body.get("state"), "change_request", cur.path("number").asText(ref))
                            .change("state", cur.path("state").asText(null), (String) body.get("state"))
                            .set("close_code", (String) body.get("close_code"))
                            .set("close_notes", (String) body.get("close_notes"))
                            .build(),
                    key -> {
                        Map<String, Object> payload = new LinkedHashMap<>(body);
                        payload.remove("number");
                        return sn.post(c, "/" + ref + "/state", payload, key);
                    });
        });
    }
}
