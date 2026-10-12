package com.acme.itsm.mcp.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Per-server settings. Each MCP server is deployed and versioned on its own, with its own timeouts and
 * its own rate limit against the instance.
 */
@ConfigurationProperties(prefix = "itsm.mcp")
public class McpCommonProperties {

    /** Path the stateless MCP endpoint is served on. */
    private String endpoint = "/mcp";

    private final ServiceNow servicenow = new ServiceNow();
    private final Confirmation confirmation = new Confirmation();
    private final RateLimit rateLimit = new RateLimit();

    public static class ServiceNow {
        /** e.g. https://acme.service-now.com */
        private String instanceUrl;
        /** Scoped app namespace. */
        private String namespace = "x_1320561_itsm_ai";
        /** Scripted REST API id, e.g. "incident". */
        private String api;
        private String version = "v1";
        private Duration connectTimeout = Duration.ofSeconds(3);
        private Duration readTimeout = Duration.ofSeconds(15);

        public String getInstanceUrl() { return instanceUrl; }
        public void setInstanceUrl(String instanceUrl) { this.instanceUrl = instanceUrl; }
        public String getNamespace() { return namespace; }
        public void setNamespace(String namespace) { this.namespace = namespace; }
        public String getApi() { return api; }
        public void setApi(String api) { this.api = api; }
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        public Duration getConnectTimeout() { return connectTimeout; }
        public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
        public Duration getReadTimeout() { return readTimeout; }
        public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }

        /** ServiceNow's versioned Scripted REST path: /api/{namespace}/{version}/{api_id}. */
        public String basePath() {
            return "/api/" + namespace + "/" + version + "/" + api;
        }
    }

    public static class Confirmation {
        /** HMAC secret shared by all replicas of this server. At least 32 characters. */
        private String secret;
        private Duration ttl = Duration.ofMinutes(10);

        public String getSecret() { return secret; }
        public void setSecret(String secret) { this.secret = secret; }
        public Duration getTtl() { return ttl; }
        public void setTtl(Duration ttl) { this.ttl = ttl; }
    }

    public static class RateLimit {
        /** Sustained calls per second this server may make to the instance. */
        private double permitsPerSecond = 10;
        private int burst = 20;
        private Duration maxWait = Duration.ofSeconds(2);

        public double getPermitsPerSecond() { return permitsPerSecond; }
        public void setPermitsPerSecond(double permitsPerSecond) { this.permitsPerSecond = permitsPerSecond; }
        public int getBurst() { return burst; }
        public void setBurst(int burst) { this.burst = burst; }
        public Duration getMaxWait() { return maxWait; }
        public void setMaxWait(Duration maxWait) { this.maxWait = maxWait; }
    }

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public ServiceNow getServicenow() { return servicenow; }
    public Confirmation getConfirmation() { return confirmation; }
    public RateLimit getRateLimit() { return rateLimit; }
}
