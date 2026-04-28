package com.coreeng.supportbot;

import static com.coreeng.supportbot.testkit.UserRole.tenant;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import com.coreeng.supportbot.testkit.Config;
import com.coreeng.supportbot.testkit.SlackWiremock;
import com.coreeng.supportbot.testkit.SupportBotClient;
import com.coreeng.supportbot.testkit.TestKit;
import com.coreeng.supportbot.testkit.TestKitExtension;
import com.google.common.collect.ImmutableList;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(TestKitExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AnalysisApiTests {

    private Config config;
    private SlackWiremock slackWiremock;
    private TestKit testKit;
    private SupportBotClient supportBotClient;

    private String baseUrl() {
        return config.supportBot().baseUrl();
    }

    private void seedClosedTicketForAnalysis() {
        var ticket = testKit.as(tenant).ticket().create(builder -> builder.message("Analysis functional test query"));

        var closeFlowStubs = ticket.stubCloseFlow("analysis ticket closed");
        var closeRequest = SupportBotClient.UpdateTicketRequest.builder()
                .status("closed")
                .authorsTeam("wow")
                .tags(ImmutableList.of("ingresses", "networking"))
                .impact("productionBlocking")
                .build();
        var closedTicket = supportBotClient.updateTicket(ticket.id(), closeRequest);

        assertThat(closedTicket.status()).isEqualTo("closed");
        closeFlowStubs.awaitAllCalled(Duration.ofSeconds(1));
    }

    @Test
    @Order(1)
    void analysisEnabled_returnsTrue() {
        // when
        var response = given().when()
                .get(baseUrl() + "/analysis/enabled")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .jsonPath();

        // then
        assertThat(response.getBoolean("enabled")).isTrue();
    }

    @Test
    @Order(2)
    void analysisRun_startsAndCompletes() {
        // given - analysis fetches threads from Slack for all tickets in the DB
        seedClosedTicketForAnalysis();

        slackWiremock.stubFor(post("/api/conversations.replies")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "ok": true,
                                    "messages": [
                                        {"type": "message", "text": "I need help with my ingress not working", "ts": "1234567890.000001"},
                                        {"type": "message", "text": "My GitHub Actions workflow is not running", "ts": "1234567890.000002"}
                                    ]
                                }
                                """)));

        // when
        given().when().post(baseUrl() + "/analysis/run?days=365").then().statusCode(202);

        // then
        Awaitility.await()
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(() -> {
                    var status = given().when()
                            .get(baseUrl() + "/analysis/status")
                            .then()
                            .statusCode(200)
                            .extract()
                            .body()
                            .jsonPath();

                    assertThat(status.getBoolean("running")).isFalse();
                    assertThat(status.getString("error")).isNull();
                });

        slackWiremock.cleanupTestStubs();
    }

    @Test
    @Order(3)
    void analysisRun_returns400_forInvalidDays() {
        given().when().post(baseUrl() + "/analysis/run?days=0").then().statusCode(400);
    }

    @Test
    @Order(4)
    void analysisResults_includeQueryTimestampAndTicketId() {
        var response = given().when()
                .get(baseUrl() + "/summary-data/results")
                .then()
                .statusCode(200)
                .extract()
                .body()
                .jsonPath();

        assertThat(response.getList("supportAreas")).isNotEmpty();
        assertThat(response.getString("supportAreas[0].queries[0].text")).isNotBlank();
        assertThat(response.getString("supportAreas[0].queries[0].timestamp")).isNotBlank();
        assertThat(response.getString("supportAreas[0].queries[0].ticketId")).isNotBlank();
    }
}
