package com.coreeng.supportbot.testkit;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;
import java.time.Instant;
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

    @Test
    void createTicketUsesJackson3ForRequestAndResponseContracts() {
        MessageTs queryTs = MessageTs.now(123456);
        MessageTs createdTs = MessageTs.now(654321);
        Instant queryDate = Instant.parse("2026-01-02T03:04:05Z");
        server.stubFor(post(urlPathEqualTo("/test/ticket"))
                .withHeader("X-Test-User", equalTo("test@functional.test"))
                .withHeader("X-Test-Role", equalTo("support"))
                .withRequestBody(equalToJson("""
                        {
                          "channelId": "C123",
                          "queryTs": "%s",
                          "createdMessageTs": "%s"
                        }
                        """.formatted(queryTs, createdTs)))
                .willReturn(okJson("""
                        {
                          "id": 42,
                          "query": {
                            "link": "https://example.test/query",
                            "date": "%s",
                            "ts": "%s",
                            "text": "Question"
                          },
                          "formMessage": {"ts": "%s"},
                          "channelId": "C123",
                          "tags": ["bug", "support"],
                          "serverAddedField": "ignored"
                        }
                        """.formatted(queryDate, queryTs, createdTs))));

        var response = client().test()
                .createTicket(SupportBotClient.TicketToCreateRequest.builder()
                        .channelId("C123")
                        .queryTs(queryTs)
                        .createdMessageTs(createdTs)
                        .build());

        assertThat(response.id()).isEqualTo(42L);
        assertThat(response.channelId()).isEqualTo("C123");
        assertThat(response.query().date()).isEqualTo(queryDate);
        assertThat(response.query().ts()).isEqualTo(queryTs);
        assertThat(response.formMessage().ts()).isEqualTo(createdTs);
        assertThat(response.tags()).containsExactly("bug", "support");
        server.verify(1, postRequestedFor(urlPathEqualTo("/test/ticket")));
    }
}
