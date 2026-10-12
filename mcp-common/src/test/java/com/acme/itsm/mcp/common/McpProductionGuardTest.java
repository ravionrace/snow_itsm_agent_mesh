package com.acme.itsm.mcp.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class McpProductionGuardTest {

    @Test
    void productionRejectsDevelopmentDefaults() {
        McpCommonProperties p = new McpCommonProperties();
        p.getServicenow().setInstanceUrl("http://localhost:8080");
        p.getConfirmation().setSecret(McpProductionGuard.DEV_CONFIRMATION_SECRET);
        assertThat(McpProductionGuard.check("production", p, "")).hasSize(3);
        assertThat(McpProductionGuard.check("development", p, "")).isEmpty();
    }

    @Test
    void productionAcceptsProperSettings() {
        McpCommonProperties p = new McpCommonProperties();
        p.getServicenow().setInstanceUrl("https://acme.service-now.com");
        p.getConfirmation().setSecret("c".repeat(40));
        assertThat(McpProductionGuard.check("production", p, "m".repeat(40))).isEmpty();
    }
}
