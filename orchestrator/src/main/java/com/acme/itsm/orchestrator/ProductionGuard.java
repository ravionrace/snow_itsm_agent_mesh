package com.acme.itsm.orchestrator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * With itsm.environment=production the API refuses to start on settings that are only safe on a developer
 * machine, and lists every problem at once so a deployment can be fixed in one pass.
 */
@Component
public class ProductionGuard implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(ProductionGuard.class);
    private final OrchestratorProperties props;

    public ProductionGuard(OrchestratorProperties props) {
        this.props = props;
    }

    @Override
    public void afterPropertiesSet() {
        List<String> problems = check(props);
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Refusing to start in production:\n - " + String.join("\n - ", problems));
        }
        if (props.isProduction()) {
            log.info("Production configuration checks passed (token mode {})", props.getServicenow().getTokenMode());
        }
    }

    static List<String> check(OrchestratorProperties props) {
        List<String> p = new ArrayList<>();
        if (!props.isProduction()) {
            return p;
        }
        var sn = props.getServicenow();
        if (props.getAuth().getMode() != OrchestratorProperties.AuthMode.JWT) {
            p.add("itsm.auth.mode must be JWT (DEV trusts an unauthenticated header)");
        }
        if (sn.getTokenMode() == OrchestratorProperties.TokenMode.STATIC) {
            p.add("itsm.servicenow.token-mode STATIC is for development only; use SIGNED_ASSERTION");
        }
        if (sn.getInstanceUrl() == null || !sn.getInstanceUrl().startsWith("https://")) {
            p.add("itsm.servicenow.instance-url must be an https:// URL");
        }
        if (sn.getTokenMode() == OrchestratorProperties.TokenMode.SIGNED_ASSERTION) {
            require(p, sn.getClientId(), "itsm.servicenow.client-id");
            require(p, sn.getKeyId(), "itsm.servicenow.key-id");
            if (sn.getClientSecretFile() == null || sn.getClientSecretFile().isBlank()) {
                p.add("itsm.servicenow.client-secret-file must point to a mounted secret (not an inline value)");
            } else if (!Files.isReadable(Path.of(sn.getClientSecretFile()))) {
                p.add("client secret file is not readable: " + sn.getClientSecretFile());
            }
            if (sn.getPrivateKeyFile() == null || !Files.isReadable(Path.of(sn.getPrivateKeyFile()))) {
                p.add("itsm.servicenow.private-key-file must point to a readable PKCS#8 key");
            }
            if (sn.getAssertionTtl().toSeconds() > 300) {
                p.add("itsm.servicenow.assertion-ttl should be 5 minutes or less");
            }
        }
        String runtimeKey = safe(() -> props.getRuntime().getApiKey());
        if (runtimeKey == null || runtimeKey.length() < 32) {
            p.add("itsm.runtime.api-key(-file) must be set (32+ characters) so only this API can call the runtime");
        }
        if (props.getRuntime().getUrl() == null || props.getRuntime().getUrl().isBlank()) {
            p.add("itsm.runtime.url is required");
        }
        if (props.getChannel().isEnabled()) {
            String secret = safe(() -> props.getChannel().getSecret());
            if (props.getChannel().getSecretFile() == null || props.getChannel().getSecretFile().isBlank()) {
                p.add("itsm.channel.secret-file must point to a mounted secret when the ServiceNow channel is enabled");
            } else if (secret == null || decodedLength(secret) < 32) {
                p.add("the ServiceNow channel secret must be base64 of 32+ random bytes");
            }
        }
        if (!"mongo".equalsIgnoreCase(props.getSessions().getStore())) {
            p.add("itsm.sessions.store must be mongo (memory loses sessions and is per-replica)");
        }
        String vault = safe(() -> props.getSessions().getVaultKey());
        if (vault == null || vault.isBlank()) {
            p.add("itsm.sessions.vault-key(-file) is required to encrypt the PII vault");
        }
        return p;
    }

    private static void require(List<String> p, String value, String name) {
        if (value == null || value.isBlank()) {
            p.add(name + " is required");
        }
    }

    private static int decodedLength(String b64) {
        try {
            return java.util.Base64.getDecoder().decode(b64.strip()).length;
        } catch (IllegalArgumentException e) {
            return 0;
        }
    }

    private static String safe(java.util.function.Supplier<String> s) {
        try {
            return s.get();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
