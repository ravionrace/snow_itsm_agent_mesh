package com.acme.itsm.mcp.catalog;

import com.acme.itsm.mcp.common.Args;
import com.acme.itsm.mcp.common.ConfirmationService;
import com.acme.itsm.mcp.common.Preview;
import com.acme.itsm.mcp.common.ServiceNowClient;
import com.acme.itsm.mcp.common.ToolException;
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
import java.util.TreeMap;

/**
 * Catalog MCP server. Calls /api/x_1320561_itsm_ai/v1/catalog, which uses sn_sc.CartJS / sn_sc.CatItem so
 * user criteria (entitlements) are evaluated for the signed-in user.
 */
@Component
public class CatalogTools {

    static final String CONFIRM_HELP = "Leave empty. Set only by the platform after the user clicks Confirm.";
    static final int MAX_VARIABLES = 60;

    private final ServiceNowClient sn;
    private final ConfirmationService confirmations;
    private final ToolExecutor exec;
    private final ToolResponses responses;

    public CatalogTools(ServiceNowClient sn, ConfirmationService confirmations, ToolExecutor exec, ToolResponses responses) {
        this.sn = sn;
        this.confirmations = confirmations;
        this.exec = exec;
        this.responses = responses;
    }

    @McpTool(name = "search_catalog", description = """
            Find catalog items the signed-in user is entitled to order, by keywords. Returns item sys_ids, names \
            and short descriptions.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public CallToolResult searchCatalog(McpTransportContext ctx,
            @McpToolParam(description = "What the user wants, e.g. 'laptop', 'VPN access'") String text,
            @McpToolParam(description = "Max results, 1-20 (default 8)", required = false) Integer limit) {
        return exec.run("search_catalog", ctx, c -> responses.ok(sn.get(c, "/items",
                Map.of("text", Args.required(text, "text", 200), "limit", Args.limit(limit, 8, 20)))));
    }

    @McpTool(name = "get_item_form", description = """
            Get a catalog item's form: variables with their names, types, choices and which are mandatory. Call this \
            before asking the user for answers, and ask only for what the form needs.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public CallToolResult getItemForm(McpTransportContext ctx,
            @McpToolParam(description = "Catalog item sys_id from search_catalog") String item) {
        return exec.run("get_item_form", ctx, c -> responses.ok(sn.get(c, "/items/" + Args.sysId(item, "item") + "/form")));
    }

    @McpTool(name = "validate_variables", description = """
            Check the user's answers against the item's form (mandatory fields, choice values, formats) before \
            ordering. Returns a list of problems, empty when the answers are valid.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public CallToolResult validateVariables(McpTransportContext ctx,
            @McpToolParam(description = "Catalog item sys_id") String item,
            @McpToolParam(description = "Answers keyed by variable name, e.g. {\"model\":\"macbook_pro_14\"}") Map<String, Object> variables) {
        return exec.run("validate_variables", ctx, c -> {
            String id = Args.sysId(item, "item");
            return responses.ok(sn.post(c, "/items/" + id + "/validate", Map.of("variables", clean(variables)), null));
        });
    }

    @McpTool(name = "order_item", description = """
            Order a catalog item (order now) with validated answers. Creates a REQ and RITM. The answers are \
            validated again before the preview. Returns a preview first.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false))
    public CallToolResult orderItem(McpTransportContext ctx,
            @McpToolParam(description = "Catalog item sys_id") String item,
            @McpToolParam(description = "Answers keyed by variable name") Map<String, Object> variables,
            @McpToolParam(description = "Quantity, 1-10 (default 1)", required = false) Integer quantity,
            @McpToolParam(description = "Requested-for user sys_id; omit to order for yourself", required = false) String requested_for,
            @McpToolParam(description = CONFIRM_HELP, required = false) String confirmation_id) {
        return exec.run("order_item", ctx, c -> {
            String id = Args.sysId(item, "item");
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("item", id);
            body.put("variables", clean(variables));
            body.put("quantity", Args.range(quantity == null ? 1 : quantity, "quantity", 1, 10));
            body.put("requested_for", Args.optionalSysId(requested_for, "requested_for"));

            JsonNode validation = sn.post(c, "/items/" + id + "/validate", Map.of("variables", body.get("variables")), null);
            if (validation.path("errors").size() > 0) {
                throw ToolException.invalid("The answers are not valid: " + validation.path("errors"),
                        "Ask the user to fix the listed answers, then call order_item again.");
            }

            return confirmations.twoStep(c, "order_item", body, confirmation_id,
                    () -> {
                        Preview.Builder p = Preview.of("Order " + validation.path("item_name").asText("catalog item"),
                                "sc_req_item", "(new)")
                                .set("quantity", String.valueOf(body.get("quantity")))
                                .set("requested_for", (String) body.get("requested_for"));
                        @SuppressWarnings("unchecked")
                        Map<String, Object> vars = (Map<String, Object>) body.get("variables");
                        vars.forEach((k, v) -> p.set("variable." + k, String.valueOf(v)));
                        String price = validation.path("price").asText("");
                        if (!price.isBlank()) {
                            p.warning("Price: " + price);
                        }
                        return p.build();
                    },
                    key -> {
                        Map<String, Object> payload = new LinkedHashMap<>(body);
                        payload.remove("item");
                        return sn.post(c, "/items/" + id + "/order", payload, key);
                    });
        });
    }

    @McpTool(name = "get_ritm_status", description = """
            Get a requested item's stage, approvals and fulfilment tasks by RITM number or sys_id.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public CallToolResult getRitmStatus(McpTransportContext ctx,
            @McpToolParam(description = "RITM number or sys_id") String number) {
        return exec.run("get_ritm_status", ctx, c -> responses.ok(sn.get(c, "/ritm/" + Args.recordRef(number, "number", Args.RITM))));
    }

    @McpTool(name = "list_my_requests", description = """
            List the signed-in user's requested items (RITMs), open ones by default.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public CallToolResult listMyRequests(McpTransportContext ctx,
            @McpToolParam(description = "open (default) | closed | all", required = false) String state,
            @McpToolParam(description = "Max results, 1-25 (default 10)", required = false) Integer limit) {
        return exec.run("list_my_requests", ctx, c -> responses.ok(sn.get(c, "/my_requests", Map.of(
                "state", state == null || state.isBlank() ? "open" : Args.oneOf(state, "state", "open", "closed", "all"),
                "limit", Args.limit(limit, 10, 25)))));
    }

    @McpTool(name = "cancel_request", description = """
            Cancel one of the user's open requested items (RITM). Returns a preview first.""",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = true))
    public CallToolResult cancelRequest(McpTransportContext ctx,
            @McpToolParam(description = "RITM number or sys_id") String number,
            @McpToolParam(description = "Why the request is being cancelled") String reason,
            @McpToolParam(description = CONFIRM_HELP, required = false) String confirmation_id) {
        return exec.run("cancel_request", ctx, c -> {
            String ref = Args.recordRef(number, "number", Args.RITM);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("number", ref);
            body.put("reason", Args.required(reason, "reason", 1000));
            JsonNode cur = sn.get(c, "/ritm/" + ref);
            if (!cur.path("active").asBoolean(true)) {
                throw ToolException.blocked("RITM_CLOSED", ref + " is already closed or cancelled.",
                        "Tell the user the request is no longer open.");
            }
            return confirmations.twoStep(c, "cancel_request", body, confirmation_id,
                    () -> Preview.of("Cancel request", "sc_req_item", cur.path("number").asText(ref))
                            .change("stage", cur.path("stage").asText(null), "Request Cancelled")
                            .set("reason", (String) body.get("reason"))
                            .build(),
                    key -> sn.post(c, "/ritm/" + ref + "/cancel", Map.of("reason", body.get("reason")), key));
        });
    }

    /** Sorted copy with string keys and scalar values only; rejects nested objects the form can't take. */
    static Map<String, Object> clean(Map<String, Object> variables) {
        if (variables == null) {
            return new TreeMap<>();
        }
        if (variables.size() > MAX_VARIABLES) {
            throw ToolException.invalid("Too many variables.", "Pass only the item's form variables.");
        }
        Map<String, Object> out = new TreeMap<>();
        variables.forEach((k, v) -> {
            if (!k.matches("^[A-Za-z0-9_]{1,80}$")) {
                throw ToolException.invalid("Invalid variable name '" + k + "'.", "Use the names from get_item_form.");
            }
            if (v instanceof Map<?, ?> || v instanceof Iterable<?>) {
                throw ToolException.invalid("Variable '" + k + "' must be a single value.",
                        "For multi-choice variables pass a comma-separated string.");
            }
            if (v != null && String.valueOf(v).length() > 4000) {
                throw ToolException.invalid("Variable '" + k + "' is too long.", "Shorten the answer.");
            }
            out.put(k, v);
        });
        return out;
    }
}
