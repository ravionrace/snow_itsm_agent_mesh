package com.acme.itsm.mcp.common;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

/**
 * Wraps every tool body: resolves the caller, turns failures into the standard envelope, and writes one
 * structured log line per call. The audit row of record is written by the scoped app in ServiceNow;
 * the orchestrator keeps its own summary in MySQL.
 */
public class ToolExecutor {

    private static final Logger audit = LoggerFactory.getLogger("itsm.tool-audit");

    private final ToolResponses responses;
    private final String server;

    public ToolExecutor(ToolResponses responses, String server) {
        this.responses = responses;
        this.server = server;
    }

    /**
     * @return the tool result; {@code isError} is set for ERROR and BLOCKED so MCP clients see the failure,
     *         while the JSON body still carries {@code next_step} for the agent
     */
    public CallToolResult run(String tool, McpTransportContext ctx, Function<CallerContext, String> body) {
        long start = System.nanoTime();
        CallerContext caller = null;
        String outcome = "OK";
        try {
            caller = CallerContext.from(ctx);
            String result = body.apply(caller);
            if (result.contains("\"status\":\"PENDING_CONFIRMATION\"")) {
                outcome = "PREVIEW";
            }
            return result(result, false);
        } catch (ToolException e) {
            outcome = e.kind() + ":" + e.code();
            return result(responses.failure(e), true);
        } catch (RuntimeException e) {
            outcome = "ERROR:INTERNAL";
            audit.error("server={} tool={} unexpected failure", server, tool, e);
            return result(responses.failure(new ToolException(ToolException.Kind.ERROR, "INTERNAL",
                    "The tool failed unexpectedly.", "Tell the user the request could not be completed. Do not retry.")), true);
        } finally {
            long ms = (System.nanoTime() - start) / 1_000_000;
            audit.info("server={} tool={} user={} request={} session={} outcome={} latency_ms={}", server, tool,
                    caller == null ? "-" : caller.userId(), caller == null ? "-" : caller.requestId(),
                    caller == null ? "-" : caller.sessionId(), outcome, ms);
        }
    }

    private static CallToolResult result(String json, boolean error) {
        return CallToolResult.builder().addTextContent(json).isError(error).build();
    }
}
