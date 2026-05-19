package com.coreeng.supportbot;

import static com.coreeng.supportbot.testkit.UserRole.tenant;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static org.assertj.core.api.Assertions.assertThat;

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

    private SlackWiremock slackWiremock;
    private TestKit testKit;
    private SupportBotClient supportBotClient;

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
        assertThat(supportBotClient.analysis().enabled()).isTrue();
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
        assertThat(supportBotClient.analysis().runStatusCode(365)).isEqualTo(202);

        // then
        Awaitility.await()
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(() -> {
                    var status = supportBotClient.analysis().status();

                    assertThat(status.running()).isFalse();
                    assertThat(status.error()).isNull();
                });

        slackWiremock.cleanupTestStubs();
    }

    @Test
    @Order(3)
    void analysisRun_returns400_forInvalidDays() {
        assertThat(supportBotClient.analysis().runStatusCode(0)).isEqualTo(400);
    }

    @Test
    @Order(4)
    void analysisResults_includeQueryTimestampAndTicketId() {
        var response = supportBotClient.analysis().results();

        assertThat(response.supportAreas()).isNotEmpty();
        assertThat(response.supportAreas().getFirst().queries()).isNotEmpty();
        assertThat(response.supportAreas().getFirst().queries().getFirst().text())
                .isNotBlank();
        assertThat(response.supportAreas().getFirst().queries().getFirst().timestamp())
                .isNotBlank();
        assertThat(response.supportAreas().getFirst().queries().getFirst().ticketId())
                .isNotBlank();
    }
}
