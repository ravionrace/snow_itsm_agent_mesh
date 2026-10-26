package com.scruttlabs.snow.client.auth;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.scruttlabs.snow.client.SnowClientConfig;
import com.scruttlabs.snow.client.http.SnowApiException;
import com.scruttlabs.snow.client.http.SnowHttpClient;

class OAuthPasswordAuthTest {

    private static final String TOKEN_PATH = "/oauth_token.do";
    private static final OAuthPasswordAuth.Credentials CREDENTIALS =
            new OAuthPasswordAuth.Credentials("client-1", "s3cret&=", "sa.springboot", "p@ss word");

    private WireMockServer server;
    private TokenProviderTest.MutableClock clock;
    private OAuthPasswordAuth auth;

    @BeforeEach
    void setUp() {
        server = new WireMockServer(options().dynamicPort());
        server.start();
        clock = new TokenProviderTest.MutableClock(Instant.parse("2026-10-25T10:00:00Z"));
        SnowClientConfig config = new SnowClientConfig(URI.create(server.baseUrl()),
                Duration.ofSeconds(2), Duration.ofSeconds(5), 0);
        auth = new OAuthPasswordAuth(config, SnowHttpClient.newHttpClient(config), SnowHttpClient.newObjectMapper(),
                CREDENTIALS, clock, Duration.ofSeconds(60));
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void postsThePasswordGrantFormEncoded() {
        server.stubFor(post(urlPathEqualTo(TOKEN_PATH))
                .willReturn(okJson("{\"access_token\": \"abc\", \"expires_in\": 1799, \"token_type\": \"Bearer\"}")));

        assertThat(auth.authorizationHeader()).isEqualTo("Bearer abc");

        server.verify(postRequestedFor(urlPathEqualTo(TOKEN_PATH))
                .withHeader("Content-Type", containing("application/x-www-form-urlencoded"))
                .withRequestBody(containing("grant_type=password"))
                .withRequestBody(containing("client_secret=s3cret%26%3D"))
                .withRequestBody(containing("password=p%40ss+word")));
    }

    @Test
    void reusesTheTokenUntilItNearsExpiry() {
        server.stubFor(post(urlPathEqualTo(TOKEN_PATH)).inScenario("t").whenScenarioStateIs(STARTED)
                .willReturn(okJson("{\"access_token\": \"first\", \"expires_in\": 300}")).willSetStateTo("2"));
        server.stubFor(post(urlPathEqualTo(TOKEN_PATH)).inScenario("t").whenScenarioStateIs("2")
                .willReturn(okJson("{\"access_token\": \"second\", \"expires_in\": 300}")));

        assertThat(auth.authorizationHeader()).isEqualTo("Bearer first");
        clock.advance(Duration.ofSeconds(200));
        assertThat(auth.authorizationHeader()).isEqualTo("Bearer first");
        clock.advance(Duration.ofSeconds(41));      // 241 s: within 60 s of the 300 s expiry
        assertThat(auth.authorizationHeader()).isEqualTo("Bearer second");
    }

    @Test
    void unauthorizedDropsOnlyTheRejectedToken() {
        server.stubFor(post(urlPathEqualTo(TOKEN_PATH)).inScenario("t").whenScenarioStateIs(STARTED)
                .willReturn(okJson("{\"access_token\": \"first\", \"expires_in\": 1800}")).willSetStateTo("2"));
        server.stubFor(post(urlPathEqualTo(TOKEN_PATH)).inScenario("t").whenScenarioStateIs("2")
                .willReturn(okJson("{\"access_token\": \"second\", \"expires_in\": 1800}")));

        String rejected = auth.authorizationHeader();
        auth.onUnauthorized(rejected);
        assertThat(auth.authorizationHeader()).isEqualTo("Bearer second");

        auth.onUnauthorized(rejected);              // a late 401 for the old token
        assertThat(auth.authorizationHeader()).isEqualTo("Bearer second");
        assertThat(server.getAllServeEvents()).hasSize(2);
    }

    @Test
    void rejectedCredentialsFailWithTheOAuthError() {
        server.stubFor(post(urlPathEqualTo(TOKEN_PATH)).willReturn(aResponse().withStatus(401)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error_description\": \"access_denied\", \"error\": \"server_error\"}")));

        assertThatThrownBy(auth::authorizationHeader)
                .isInstanceOfSatisfying(SnowApiException.class, e -> {
                    assertThat(e.status()).isEqualTo(401);
                    assertThat(e.detail()).isEqualTo("server_error access_denied");
                    assertThat(e.getMessage()).doesNotContain("s3cret").doesNotContain("p@ss");
                });
    }

    @Test
    void credentialsNeverPrintSecrets() {
        assertThat(CREDENTIALS.toString()).doesNotContain("s3cret").doesNotContain("p@ss").contains("client-1");
    }
}
