package com.coreeng.supportbot.testkit;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SupportBotClientAuthHeadersTest {
    private RequestSpecification originalRequestSpecification;
    private WireMockServer server;

    @BeforeEach
    void setUp() {
        originalRequestSpecification = RestAssured.requestSpecification;
        RestAssured.requestSpecification = null;

        server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop();
        RestAssured.requestSpecification = originalRequestSpecification;
    }

    private SupportBotClient client() {
        return new SupportBotClient(server.baseUrl(), null);
    }

    @Test
    void analysisEnabledSendsTestBypassHeaders() {
        server.stubFor(get(urlPathEqualTo("/analysis/enabled"))
                .withHeader("X-Test-User", equalTo("test@functional.test"))
                .withHeader("X-Test-Role", equalTo("support"))
                .willReturn(okJson("""
                        {"enabled": true}
                        """)));

        assertThat(client().analysis().enabled()).isTrue();
    }

    @Test
    void findTicketByQueryTsSendsTestBypassHeaders() {
        MessageTs queryTs = MessageTs.now();
        MessageTs formMessageTs = MessageTs.now();

        server.stubFor(get(urlPathEqualTo("/test/ticket/by-query"))
                .withQueryParam("channelId", equalTo("C123"))
                .withQueryParam("messageTs", equalTo(queryTs.toString()))
                .withHeader("X-Test-User", equalTo("test@functional.test"))
                .withHeader("X-Test-Role", equalTo("support"))
                .willReturn(okJson("""
                        {
                          "id": 42,
                          "query": {"ts": "%s"},
                          "formMessage": {"ts": "%s"},
                          "channelId": "C123"
                        }
                        """.formatted(queryTs, formMessageTs))));

        var result = client().findTicketByQueryTs("C123", queryTs);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(42L);
        assertThat(result.formMessage().ts()).isEqualTo(formMessageTs);
    }

    @Test
    void findTicketByQueryTsReturnsNullWhenEndpointReturnsNotFound() {
        MessageTs queryTs = MessageTs.now();

        server.stubFor(get(urlPathEqualTo("/test/ticket/by-query"))
                .withQueryParam("channelId", equalTo("C123"))
                .withQueryParam("messageTs", equalTo(queryTs.toString()))
                .willReturn(com.github.tomakehurst.wiremock.client.WireMock.aResponse()
                        .withStatus(404)));

        assertThat(client().findTicketByQueryTs("C123", queryTs)).isNull();
    }
}
