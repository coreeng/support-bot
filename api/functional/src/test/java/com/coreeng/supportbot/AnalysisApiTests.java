package com.coreeng.supportbot;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import com.coreeng.supportbot.testkit.Config;
import com.coreeng.supportbot.testkit.SlackWiremock;
import com.coreeng.supportbot.testkit.TestKitExtension;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(TestKitExtension.class)
public class AnalysisApiTests {

    private Config config;
    private SlackWiremock slackWiremock;

    private String baseUrl() {
        return config.supportBot().baseUrl();
    }

    @Test
    void analysisEnabled_returnsTrue() {
        // when
        var response = given()
                .when()
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
    void analysisRun_startsAndCompletes() {
        // given - analysis fetches threads from Slack for all tickets in the DB
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
        given()
                .when()
                .post(baseUrl() + "/analysis/run?days=365")
                .then()
                .statusCode(202);

        // then
        Awaitility.await()
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(() -> {
                    var status = given()
                            .when()
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
    void analysisRun_returns400_forInvalidDays() {
        given()
                .when()
                .post(baseUrl() + "/analysis/run?days=0")
                .then()
                .statusCode(400);
    }
}
