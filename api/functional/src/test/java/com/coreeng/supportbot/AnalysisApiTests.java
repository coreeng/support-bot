package com.coreeng.supportbot;

import static com.coreeng.supportbot.testkit.UserRole.tenant;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
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

        // The functionaltests profile runs the LLM in proxy mode against WireMock, so the wire
        // contract is asserted against a booted app: native Gemini path, the configured Basic
        // credential delivered through config binding, and no API-key header.
        var llmRequests = slackWiremock.findAll(
                postRequestedFor(urlPathMatching("/llm-proxy/v1beta/models/[^/]+:generateContent")));
        assertThat(llmRequests).isNotEmpty();
        var llmRequest = llmRequests.getFirst();
        assertThat(llmRequest.header("Authorization").values()).containsExactly("Basic ZnVuY3Rpb25hbDpwcm94eQ==");
        assertThat(llmRequest.header("x-goog-api-key").isPresent()).isFalse();

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

    @Test
    @Order(5)
    void analysisPrompt_returnsPromptText() {
        assertThat(supportBotClient.analysis().prompt().prompt())
                .contains("Platform Support Knowledge Gap & Intent Analysis Prompt");
    }

    @Test
    @Order(6)
    void analysisPrompt_returns403_forNonSupportEngineer() {
        // Not "leadership": TestAuthBypassFilter grants it SUPPORT_ENGINEER too.
        // 403, not 401 like the export endpoints: /analysis/prompt has a scoped AccessDeniedHandler.
        assertThat(supportBotClient.getStatusCodeAsRole("/analysis/prompt", "escalation"))
                .isEqualTo(403);
    }
}
