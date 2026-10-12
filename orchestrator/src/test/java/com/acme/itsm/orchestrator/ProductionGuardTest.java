package com.acme.itsm.orchestrator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionGuardTest {

    @TempDir
    Path dir;

    @Test
    void developmentIsNotChecked() {
        assertThat(ProductionGuard.check(new OrchestratorProperties())).isEmpty();
    }

    @Test
    void unsafeProductionSettingsAreAllReported() {
        OrchestratorProperties p = new OrchestratorProperties();
        p.setEnvironment("production");
        p.getAuth().setMode(OrchestratorProperties.AuthMode.DEV);
        p.getServicenow().setTokenMode(OrchestratorProperties.TokenMode.STATIC);
        p.getServicenow().setInstanceUrl("http://dev.service-now.com");
        p.getSessions().setStore("memory");
        assertThat(ProductionGuard.check(p)).anyMatch(s -> s.contains("auth.mode must be JWT"))
                .anyMatch(s -> s.contains("STATIC is for development only"))
                .anyMatch(s -> s.contains("https://"))
                .anyMatch(s -> s.contains("runtime.api-key"))
                .anyMatch(s -> s.contains("sessions.store must be mongo"))
                .anyMatch(s -> s.contains("vault-key"));
    }

    @Test
    void completeProductionSettingsPass() throws Exception {
        Path secret = Files.writeString(dir.resolve("secret"), "x");
        Path key = Files.writeString(dir.resolve("key.pem"), "-----BEGIN PRIVATE KEY-----");
        OrchestratorProperties p = new OrchestratorProperties();
        p.setEnvironment("production");
        p.getServicenow().setInstanceUrl("https://acme.service-now.com");
        p.getServicenow().setClientId("client");
        p.getServicenow().setKeyId("kid-1");
        p.getServicenow().setClientSecretFile(secret.toString());
        p.getServicenow().setPrivateKeyFile(key.toString());
        p.getRuntime().setApiKey("k".repeat(40));
        p.getSessions().setVaultKey("dmF1bHQ=");
        assertThat(ProductionGuard.check(p)).isEmpty();
    }
}
