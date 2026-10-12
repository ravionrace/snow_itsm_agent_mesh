package com.acme.itsm.mcp.common;

import java.util.ArrayList;
import java.util.List;

/**
 * With itsm.environment=production an MCP server refuses to start on development-only settings. The servers
 * hold no OAuth credentials: each call carries the user's short-lived ServiceNow token, minted by the
 * orchestrator's confidential OAuth client, so these checks cover transport and shared secrets only.
 */
public final class McpProductionGuard {

    static final String DEV_CONFIRMATION_SECRET = "dev-only-confirmation-secret-change-me-0000";

    private McpProductionGuard() {
    }

    public static List<String> check(String environment, McpCommonProperties props, String meshKey) {
        List<String> p = new ArrayList<>();
        if (!"production".equalsIgnoreCase(environment)) {
            return p;
        }
        String url = props.getServicenow().getInstanceUrl();
        if (url == null || !url.startsWith("https://")) {
            p.add("itsm.mcp.servicenow.instance-url must be an https:// URL");
        }
        if (meshKey == null || meshKey.length() < 32) {
            p.add("itsm.mcp.mesh-key (MESH_KEY) must be set, 32+ characters, so only the agent runtime can call this server");
        }
        String secret = props.getConfirmation().getSecret();
        if (secret == null || secret.length() < 32 || DEV_CONFIRMATION_SECRET.equals(secret)) {
            p.add("itsm.mcp.confirmation.secret must be a unique 32+ character secret (not the development default)");
        }
        return p;
    }
}
