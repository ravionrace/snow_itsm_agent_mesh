package com.scruttlabs.snow.client.http;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import com.scruttlabs.snow.client.SnowClientConfig;
import com.scruttlabs.snow.client.auth.AuthStrategy;

class SnowHttpClientTest {

    private static final String PATH = "/api/x_test/thing";

    private WireMockServer server;
    private FakeAuth auth;
    private List<Duration> waits;
    private SnowHttpClient client;
    private JavaType nodeType;

    @BeforeEach
    void setUp() {
        server = new WireMockServer(options().dynamicPort());
        server.start();
        auth = new FakeAuth();
        waits = new CopyOnWriteArrayList<>();
        client = newClient(d -> waits.add(d), 3);
        nodeType = client.types().constructType(JsonNode.class);
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    private SnowHttpClient newClient(SnowHttpClient.Sleeper sleeper, int maxRetries) {
        SnowClientConfig config = new SnowClientConfig(URI.create(server.baseUrl()),
                Duration.ofSeconds(2), Duration.ofSeconds(5), maxRetries);
        return new SnowHttpClient(config, auth, SnowHttpClient.newObjectMapper(), SnowHttpClient.newHttpClient(config),
                new RetryPolicy(maxRetries, Duration.ofMillis(100), Duration.ofSeconds(30), () -> 0.5),
                sleeper, Clock.systemUTC());
    }

    @Test
    void unwrapsTheResultEnvelopeAndSendsAuthorization() {
        server.stubFor(get(urlPathEqualTo(PATH)).willReturn(okJson("{\"result\": {\"answer\": 42}}")));

        JsonNode body = client.get(PATH, Map.of(), nodeType);

        assertThat(body.path("answer").asInt()).isEqualTo(42);
        server.verify(getRequestedFor(urlPathEqualTo(PATH)).withHeader("Authorization", equalTo("Bearer t1")));
    }

    @Test
    void acceptsABodyWithoutEnvelope() {
        server.stubFor(get(urlPathEqualTo(PATH)).willReturn(okJson("{\"answer\": 7}")));

        assertThat(client.<JsonNode>get(PATH, Map.of(), nodeType).path("answer").asInt()).isEqualTo(7);
    }

    @Test
    void encodesQueryParametersAndDropsBlanks() {
        server.stubFor(get(urlPathEqualTo(PATH)).willReturn(okJson("{}")));
        Map<String, String> query = new LinkedHashMap<>();
        query.put("name_prefix", "u_ a&b");
        query.put("scope", "");
        query.put("extends", null);

        client.get(PATH, query, nodeType);

        assertThat(server.getAllServeEvents()).singleElement()
                .satisfies(e -> assertThat(e.getRequest().getUrl()).isEqualTo(PATH + "?name_prefix=u_+a%26b"));
    }

    @Test
    void unauthorizedInvalidatesTheRejectedTokenAndRetriesOnce() {
        server.stubFor(get(urlPathEqualTo(PATH)).inScenario("auth").whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(401)).willSetStateTo("refreshed"));
        server.stubFor(get(urlPathEqualTo(PATH)).inScenario("auth").whenScenarioStateIs("refreshed")
                .willReturn(okJson("{\"ok\": true}")));

        JsonNode body = client.get(PATH, Map.of(), nodeType);

        assertThat(body.path("ok").asBoolean()).isTrue();
        assertThat(auth.rejected).containsExactly("Bearer t1");
        server.verify(getRequestedFor(urlPathEqualTo(PATH)).withHeader("Authorization", equalTo("Bearer t2")));
        assertThat(waits).isEmpty();
    }

    @Test
    void secondUnauthorizedIsFinal() {
        server.stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(401)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\": {\"message\": \"User Not Authenticated\", \"detail\": \"Required to provide Auth information\"}, \"status\": \"failure\"}")));

        assertThatThrownBy(() -> client.get(PATH, Map.of(), nodeType))
                .isInstanceOfSatisfying(SnowApiException.class, e -> {
                    assertThat(e.status()).isEqualTo(401);
                    assertThat(e.errorMessage()).isEqualTo("User Not Authenticated");
                    assertThat(e.detail()).isEqualTo("Required to provide Auth information");
                });
        assertThat(server.getAllServeEvents()).hasSize(2);
    }

    @Test
    void rateLimitHonoursRetryAfter() {
        server.stubFor(get(urlPathEqualTo(PATH)).inScenario("rate").whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "2")).willSetStateTo("ok"));
        server.stubFor(get(urlPathEqualTo(PATH)).inScenario("rate").whenScenarioStateIs("ok")
                .willReturn(okJson("{\"ok\": true}")));

        client.get(PATH, Map.of(), nodeType);

        assertThat(waits).containsExactly(Duration.ofSeconds(2));
    }

    @Test
    void serverErrorsAreRetriedUntilMaxRetriesThenThrown() {
        server.stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(() -> client.get(PATH, Map.of(), nodeType))
                .isInstanceOfSatisfying(SnowApiException.class, e -> assertThat(e.status()).isEqualTo(503));
        assertThat(server.getAllServeEvents()).hasSize(4);           // 1 attempt + 3 retries
        assertThat(waits).containsExactly(Duration.ofMillis(50), Duration.ofMillis(100), Duration.ofMillis(200));
    }

    @Test
    void connectionFailuresAreRetried() {
        server.stubFor(get(urlPathEqualTo(PATH)).inScenario("io").whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)).willSetStateTo("ok"));
        server.stubFor(get(urlPathEqualTo(PATH)).inScenario("io").whenScenarioStateIs("ok")
                .willReturn(okJson("{\"ok\": true}")));

        assertThat(client.<JsonNode>get(PATH, Map.of(), nodeType).path("ok").asBoolean()).isTrue();
        assertThat(waits).hasSize(1);
    }

    @Test
    void persistentConnectionFailureEndsAsUncheckedIOException() {
        server.stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        assertThatThrownBy(() -> client.get(PATH, Map.of(), nodeType)).isInstanceOf(UncheckedIOException.class);
    }

    @Test
    void clientErrorsAreNotRetried() {
        server.stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(404)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\": {\"message\": \"Table u_x was not found or is not visible to you.\", \"detail\": \"\"}}")));

        assertThatThrownBy(() -> client.get(PATH, Map.of(), nodeType))
                .isInstanceOfSatisfying(SnowApiException.class, e -> {
                    assertThat(e.status()).isEqualTo(404);
                    assertThat(e.errorMessage()).contains("was not found");
                    assertThat(e.retryable()).isFalse();
                });
        assertThat(server.getAllServeEvents()).hasSize(1);
        assertThat(waits).isEmpty();
    }

    @Test
    void interruptDuringBackoffStopsAndKeepsTheFlag() {
        server.stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(503)));
        SnowHttpClient interrupted = newClient(d -> {
            throw new InterruptedException("stop");
        }, 3);

        try {
            assertThatThrownBy(() -> interrupted.get(PATH, Map.of(), nodeType))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("Interrupted");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            assertThat(server.getAllServeEvents()).hasSize(1);
        } finally {
            Thread.interrupted();   // clear the flag so it does not leak into other tests
        }
    }

    @Test
    void rejectsPathsOutsideTheApi() {
        assertThatThrownBy(() -> client.get("/oauth_token.do", Map.of(), nodeType))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** Hands out t1, t2, ... and moves on to the next token when told the current one was rejected. */
    private static final class FakeAuth implements AuthStrategy {
        final AtomicInteger version = new AtomicInteger(1);
        final List<String> rejected = new CopyOnWriteArrayList<>();

        @Override
        public String authorizationHeader() {
            return "Bearer t" + version.get();
        }

        @Override
        public void onUnauthorized(String rejectedHeader) {
            rejected.add(rejectedHeader);
            version.incrementAndGet();
        }
    }
}
