package com.acme.itsm.mcp.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.transport.WebMvcStatelessServerTransport;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.ai.mcp.server.autoconfigure.McpServerStatelessWebMvcAutoConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.function.ServerRequest;

import java.io.IOException;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

/**
 * Wires the shared plumbing into every MCP server. Registered before Spring AI's stateless WebMvc
 * auto-configuration so our transport (which copies the caller's headers into the MCP transport
 * context) replaces the default one.
 */
@AutoConfiguration(before = McpServerStatelessWebMvcAutoConfiguration.class)
@EnableConfigurationProperties(McpCommonProperties.class)
public class McpCommonAutoConfiguration {

    static final String HDR_USER = "X-User-Id";
    static final String HDR_REQUEST = "X-Request-Id";
    static final String HDR_SESSION = "X-Session-Id";
    static final String HDR_IDEMPOTENCY = "X-Idempotency-Key";
    static final String HDR_MESH_KEY = "X-Mesh-Key";

    @Bean
    public WebMvcStatelessServerTransport webMvcStatelessServerTransport(ObjectMapper objectMapper,
                                                                         McpCommonProperties props) {
        return WebMvcStatelessServerTransport.builder()
                .jsonMapper(new JacksonMcpJsonMapper(objectMapper))
                .messageEndpoint(props.getEndpoint())
                .contextExtractor(McpCommonAutoConfiguration::extract)
                .build();
    }

    static McpTransportContext extract(ServerRequest request) {
        Map<String, Object> values = new HashMap<>();
        String auth = request.headers().firstHeader(HttpHeaders.AUTHORIZATION);
        if (auth != null && auth.regionMatches(true, 0, "Bearer ", 0, 7)) {
            values.put(CallerContext.KEY_TOKEN, auth.substring(7).trim());
        }
        putIfPresent(values, CallerContext.KEY_USER, request.headers().firstHeader(HDR_USER));
        putIfPresent(values, CallerContext.KEY_REQUEST, request.headers().firstHeader(HDR_REQUEST));
        putIfPresent(values, CallerContext.KEY_SESSION, request.headers().firstHeader(HDR_SESSION));
        putIfPresent(values, CallerContext.KEY_IDEMPOTENCY, request.headers().firstHeader(HDR_IDEMPOTENCY));
        return McpTransportContext.create(values);
    }

    private static void putIfPresent(Map<String, Object> values, String key, String value) {
        if (value != null && !value.isBlank()) {
            values.put(key, value);
        }
    }

    /**
     * Only the orchestrator may call MCP servers. Network policy is the primary control; this shared
     * key (or mTLS at the mesh layer) is the second.
     */
    @Bean
    public FilterRegistrationBean<OncePerRequestFilter> meshKeyFilter(McpCommonProperties props,
                                                                      @Value("${itsm.mcp.mesh-key:}") String meshKey) {
        byte[] expected = meshKey.getBytes(StandardCharsets.UTF_8);
        OncePerRequestFilter filter = new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                    throws ServletException, IOException {
                String given = req.getHeader(HDR_MESH_KEY);
                if (expected.length > 0 && (given == null
                        || !MessageDigest.isEqual(expected, given.getBytes(StandardCharsets.UTF_8)))) {
                    res.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                    return;
                }
                chain.doFilter(req, res);
            }
        };
        FilterRegistrationBean<OncePerRequestFilter> reg = new FilterRegistrationBean<>(filter);
        reg.addUrlPatterns(props.getEndpoint(), props.getEndpoint() + "/*");
        return reg;
    }

    /** Fails start-up in production on development-only settings (see McpProductionGuard). */
    @Bean
    public org.springframework.beans.factory.InitializingBean mcpProductionGuard(
            McpCommonProperties props, @Value("${itsm.environment:development}") String environment,
            @Value("${itsm.mcp.mesh-key:}") String meshKey) {
        return () -> {
            var problems = McpProductionGuard.check(environment, props, meshKey);
            if (!problems.isEmpty()) {
                throw new IllegalStateException("Refusing to start in production:\n - " + String.join("\n - ", problems));
            }
        };
    }

    @Bean
    public ToolResponses toolResponses(ObjectMapper objectMapper) {
        return new ToolResponses(objectMapper);
    }

    @Bean
    public ConfirmationService confirmationService(McpCommonProperties props, ToolResponses responses) {
        return new ConfirmationService(props.getConfirmation().getSecret(), props.getConfirmation().getTtl(),
                Clock.systemUTC(), responses);
    }

    @Bean
    public RateLimiter serviceNowRateLimiter(McpCommonProperties props) {
        McpCommonProperties.RateLimit rl = props.getRateLimit();
        return new RateLimiter(rl.getPermitsPerSecond(), rl.getBurst(), rl.getMaxWait());
    }

    @Bean
    public ServiceNowClient serviceNowClient(McpCommonProperties props, RateLimiter rateLimiter, ObjectMapper mapper) {
        return new ServiceNowClient(props.getServicenow(), rateLimiter, mapper);
    }

    @Bean
    public ToolExecutor toolExecutor(ToolResponses responses,
                                     @Value("${spring.ai.mcp.server.name:mcp}") String serverName) {
        return new ToolExecutor(responses, serverName);
    }
}
