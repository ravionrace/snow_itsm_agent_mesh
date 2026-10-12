package com.acme.itsm.mcp.cmdbkb;

import com.acme.itsm.mcp.common.McpCommonProperties;
import com.acme.itsm.mcp.common.RateLimiter;
import com.acme.itsm.mcp.common.ServiceNowClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class CmdbKbMcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(CmdbKbMcpApplication.class, args);
    }

    /** This server fronts two Scripted REST APIs: cmdb (default client) and kb. Both share one rate limit. */
    @Bean
    public ServiceNowClient kbClient(McpCommonProperties props, RateLimiter rateLimiter, ObjectMapper mapper,
                                     @Value("${cmdbkb.kb-api:kb}") String kbApi) {
        McpCommonProperties.ServiceNow source = props.getServicenow();
        McpCommonProperties.ServiceNow kb = new McpCommonProperties.ServiceNow();
        kb.setInstanceUrl(source.getInstanceUrl());
        kb.setNamespace(source.getNamespace());
        kb.setVersion(source.getVersion());
        kb.setConnectTimeout(source.getConnectTimeout());
        kb.setReadTimeout(source.getReadTimeout());
        kb.setApi(kbApi);
        return new ServiceNowClient(kb, rateLimiter, mapper);
    }
}
