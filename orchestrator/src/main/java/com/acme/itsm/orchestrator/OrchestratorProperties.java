package com.acme.itsm.orchestrator;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "itsm")
public class OrchestratorProperties {

    private final Auth auth = new Auth();
    private final Runtime runtime = new Runtime();
    private final ServiceNow servicenow = new ServiceNow();
    private final Sessions sessions = new Sessions();
    private final Limits limits = new Limits();
    private final Channel channel = new Channel();
    /** "production" turns on start-up checks that refuse unsafe settings (see ProductionGuard). */
    private String environment = "development";

    public enum AuthMode { JWT, DEV }

    public enum TokenMode {
        /**
         * Production default. The mesh is a confidential OAuth client registered in ServiceNow (OAuth JWT API
         * endpoint). For each signed-in user it signs a short-lived JWT assertion (sub = the user) with its own
         * private key and exchanges it for a ServiceNow token for that user (RFC 7523 JWT bearer grant).
         */
        SIGNED_ASSERTION,
        /** ServiceNow trusts the corporate IdP (OIDC provider config), so the user's access token is forwarded. */
        PASSTHROUGH,
        /** Exchange the user's IdP-issued JWT for a ServiceNow token with the JWT bearer grant. */
        JWT_BEARER,
        /** A fixed token from configuration. Local development against a PDI only; refused in production. */
        STATIC
    }

    public static class Auth {
        private AuthMode mode = AuthMode.JWT;
        /** JWT claim holding the user id that ServiceNow knows (e.g. preferred_username or upn). */
        private String userClaim = "preferred_username";

        public AuthMode getMode() { return mode; }
        public void setMode(AuthMode mode) { this.mode = mode; }
        public String getUserClaim() { return userClaim; }
        public void setUserClaim(String userClaim) { this.userClaim = userClaim; }
    }

    public static class Runtime {
        /** Base URL of the LangGraph agent runtime. */
        private String url = "http://localhost:8090";
        private String apiKey = "";
        private String apiKeyFile;
        private Duration timeout = Duration.ofSeconds(180);

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getApiKey() { return Secrets.read(apiKeyFile, apiKey); }
        public String getApiKeyFile() { return apiKeyFile; }
        public void setApiKeyFile(String apiKeyFile) { this.apiKeyFile = apiKeyFile; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public Duration getTimeout() { return timeout; }
        public void setTimeout(Duration timeout) { this.timeout = timeout; }
    }

    public static class ServiceNow {
        private TokenMode tokenMode = TokenMode.SIGNED_ASSERTION;
        private String instanceUrl;
        /** OAuth client id of the mesh's Application Registry entry (oauth_jwt) on the instance. */
        private String clientId;
        private String clientSecret;
        /** File containing the client secret (mounted secret); takes precedence over client-secret. */
        private String clientSecretFile;
        /** PKCS#8 PEM private key that signs assertions; its certificate is in the instance's JWT verifier map. */
        private String privateKeyFile;
        /** Key id ("kid") of the verifier map entry holding the matching certificate. Enables key rotation. */
        private String keyId;
        /** SSO claim whose value identifies the user in ServiceNow (matched against the registry's user field). */
        private String subjectClaim = "preferred_username";
        /** "aud" of the assertion; defaults to the client id. */
        private String audience;
        private Duration assertionTtl = Duration.ofSeconds(60);
        /** A cached token is replaced this long before it expires, so no request runs on an expiring token. */
        private Duration refreshMargin = Duration.ofMinutes(5);
        private int tokenCacheMaxUsers = 10_000;
        private Duration connectTimeout = Duration.ofSeconds(5);
        private Duration readTimeout = Duration.ofSeconds(15);
        private String staticToken;

        public TokenMode getTokenMode() { return tokenMode; }
        public void setTokenMode(TokenMode tokenMode) { this.tokenMode = tokenMode; }
        public String getInstanceUrl() { return instanceUrl; }
        public void setInstanceUrl(String instanceUrl) { this.instanceUrl = instanceUrl; }
        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }
        public String getClientSecret() { return clientSecret; }
        public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
        public String getClientSecretFile() { return clientSecretFile; }
        public void setClientSecretFile(String clientSecretFile) { this.clientSecretFile = clientSecretFile; }
        public String getPrivateKeyFile() { return privateKeyFile; }
        public void setPrivateKeyFile(String privateKeyFile) { this.privateKeyFile = privateKeyFile; }
        public String getKeyId() { return keyId; }
        public void setKeyId(String keyId) { this.keyId = keyId; }
        public String getSubjectClaim() { return subjectClaim; }
        public void setSubjectClaim(String subjectClaim) { this.subjectClaim = subjectClaim; }
        public String getAudience() { return audience; }
        public void setAudience(String audience) { this.audience = audience; }
        public Duration getAssertionTtl() { return assertionTtl; }
        public void setAssertionTtl(Duration assertionTtl) { this.assertionTtl = assertionTtl; }
        public Duration getRefreshMargin() { return refreshMargin; }
        public void setRefreshMargin(Duration refreshMargin) { this.refreshMargin = refreshMargin; }
        public int getTokenCacheMaxUsers() { return tokenCacheMaxUsers; }
        public void setTokenCacheMaxUsers(int tokenCacheMaxUsers) { this.tokenCacheMaxUsers = tokenCacheMaxUsers; }
        public Duration getConnectTimeout() { return connectTimeout; }
        public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
        public Duration getReadTimeout() { return readTimeout; }
        public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
        public String getStaticToken() { return staticToken; }
        public void setStaticToken(String staticToken) { this.staticToken = staticToken; }

        /** The client secret, from the mounted file when one is configured. */
        public String resolvedClientSecret() {
            return Secrets.read(clientSecretFile, clientSecret);
        }

        public String resolvedAudience() {
            return audience == null || audience.isBlank() ? clientId : audience;
        }

        public String tokenEndpoint() {
            return instanceUrl.replaceAll("/+$", "") + "/oauth_token.do";
        }
    }

    public static class Sessions {
        /** mongo or memory */
        private String store = "mongo";
        /** Base64 AES-256 key encrypting the per-session PII vault at rest. */
        private String vaultKey;
        private String vaultKeyFile;
        private Duration ttl = Duration.ofDays(7);

        public String getStore() { return store; }
        public void setStore(String store) { this.store = store; }
        public String getVaultKey() { return Secrets.read(vaultKeyFile, vaultKey); }
        public String getVaultKeyFile() { return vaultKeyFile; }
        public void setVaultKeyFile(String vaultKeyFile) { this.vaultKeyFile = vaultKeyFile; }
        public void setVaultKey(String vaultKey) { this.vaultKey = vaultKey; }
        public Duration getTtl() { return ttl; }
        public void setTtl(Duration ttl) { this.ttl = ttl; }
    }

    /**
     * Server-to-server channel for the ServiceNow in-app Assistant. The instance signs each request (HMAC-SHA256)
     * and names the signed-in user; the orchestrator then acts for that user with a per-user OAuth token.
     */
    public static class Channel {
        private boolean enabled = false;
        /** Base64 shared secret (32+ bytes), also stored in the instance's x_..._itsm_ai.mesh.channel_secret property. */
        private String secret;
        private String secretFile;
        private Duration maxSkew = Duration.ofMinutes(5);
        /** How long a finished turn's result stays available for polling. */
        private Duration resultTtl = Duration.ofMinutes(15);
        private int maxConcurrentTurns = 8;
        private int maxQueuedTurns = 50;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getSecret() { return Secrets.read(secretFile, secret); }
        public void setSecret(String secret) { this.secret = secret; }
        public String getSecretFile() { return secretFile; }
        public void setSecretFile(String secretFile) { this.secretFile = secretFile; }
        public Duration getMaxSkew() { return maxSkew; }
        public void setMaxSkew(Duration maxSkew) { this.maxSkew = maxSkew; }
        public Duration getResultTtl() { return resultTtl; }
        public void setResultTtl(Duration resultTtl) { this.resultTtl = resultTtl; }
        public int getMaxConcurrentTurns() { return maxConcurrentTurns; }
        public void setMaxConcurrentTurns(int maxConcurrentTurns) { this.maxConcurrentTurns = maxConcurrentTurns; }
        public int getMaxQueuedTurns() { return maxQueuedTurns; }
        public void setMaxQueuedTurns(int maxQueuedTurns) { this.maxQueuedTurns = maxQueuedTurns; }
    }

    public static class Limits {
        private int requestsPerMinute = 20;
        private int maxMessageChars = 4000;

        public int getRequestsPerMinute() { return requestsPerMinute; }
        public void setRequestsPerMinute(int requestsPerMinute) { this.requestsPerMinute = requestsPerMinute; }
        public int getMaxMessageChars() { return maxMessageChars; }
        public void setMaxMessageChars(int maxMessageChars) { this.maxMessageChars = maxMessageChars; }
    }

    public Auth getAuth() { return auth; }
    public Runtime getRuntime() { return runtime; }
    public ServiceNow getServicenow() { return servicenow; }
    public Sessions getSessions() { return sessions; }
    public Limits getLimits() { return limits; }
    public Channel getChannel() { return channel; }
    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }
    public boolean isProduction() { return "production".equalsIgnoreCase(environment); }
}
