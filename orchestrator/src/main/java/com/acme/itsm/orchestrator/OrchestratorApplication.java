package com.acme.itsm.orchestrator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Orchestrator API: the edge in front of the LangGraph agent runtime. It validates the user's SSO token,
 * obtains their ServiceNow token, redacts PII and screens the message before anything reaches an LLM.
 *
 * <p>Mongo auto-configuration is imported explicitly by {@code MongoSessionStore} so the API can also run
 * with in-memory sessions for local development.
 */
@SpringBootApplication(exclude = {MongoAutoConfiguration.class, MongoDataAutoConfiguration.class})
@EnableConfigurationProperties(OrchestratorProperties.class)
public class OrchestratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrchestratorApplication.class, args);
    }
}
