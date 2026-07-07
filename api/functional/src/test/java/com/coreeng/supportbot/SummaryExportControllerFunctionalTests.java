package com.coreeng.supportbot;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static org.assertj.core.api.Assertions.assertThat;

import com.coreeng.supportbot.testkit.SlackWiremock;
import com.coreeng.supportbot.testkit.SupportBotClient;
import com.coreeng.supportbot.testkit.TestKitExtension;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Exercises the real Spring Security filter chain for the export endpoints — something
 * {@code SummaryExportControllerTest} (a unit test constructing the controller directly) cannot
 * cover, since it bypasses security entirely. Also covers the full async flow (start → poll →
 * download) against mocked Slack responses, same as {@code AnalysisApiTests}, since that path also
 * needs a real executor/DB/HTTP stack that unit tests mock away.
 */
@ExtendWith(TestKitExtension.class)
public class SummaryExportControllerFunctionalTests {

    private SlackWiremock slackWiremock;
    private SupportBotClient supportBotClient;

    // TestAuthBypassFilter maps the "leadership" test role to BOTH Role.LEADERSHIP and
    // Role.SUPPORT_ENGINEER (TestAuthBypassFilter.java:46: isSupportEngineer is true whenever
    // isLeadership is true), so it can't simulate a leadership-only principal. "escalation" grants
    // only Role.ESCALATION + Role.USER — no SUPPORT_ENGINEER — making it a valid stand-in for "any
    // authenticated user without SUPPORT_ENGINEER" when proving the /summary-data/** security rule
    // (SecurityConfig.java) is enforced end-to-end, not just assumed.
    private static final String NON_SUPPORT_ENGINEER_ROLE = "escalation";

    // SecurityConfig's custom authenticationEntryPoint fires for BOTH missing authentication and
    // insufficient role (confirmed empirically: an authenticated-but-wrong-role request gets the
    // exact same {"error":"Unauthorized"} 401 body as a fully anonymous one) — there's no separate
    // 403/AccessDeniedHandler path in this app, so 401 is the correct expectation here, not 403.
    @Test
    void start_returns401_forNonSupportEngineerRole() {
        int statusCode =
                supportBotClient.postStatusCodeAsRole("/summary-data/export/start?days=7", NON_SUPPORT_ENGINEER_ROLE);

        assertThat(statusCode).isEqualTo(401);
    }

    @Test
    void status_returns401_forNonSupportEngineerRole() {
        int statusCode = supportBotClient.getStatusCodeAsRole("/summary-data/export/status", NON_SUPPORT_ENGINEER_ROLE);

        assertThat(statusCode).isEqualTo(401);
    }

    @Test
    void download_returns401_forNonSupportEngineerRole() {
        int statusCode =
                supportBotClient.getStatusCodeAsRole("/summary-data/export/download", NON_SUPPORT_ENGINEER_ROLE);

        assertThat(statusCode).isEqualTo(401);
    }

    @Test
    void status_returns200_forSupportEngineerRole() {
        assertThat(supportBotClient.export().status()).isNotNull();
    }

    @Test
    void download_returns404_forSupportEngineerRole_whenNothingReady() {
        assertThat(supportBotClient.export().downloadStatusCode()).isEqualTo(404);
    }

    @Test
    void fullExportFlow_startsAsync_completesFromMockedSlack_andIsSingleConsumption() throws IOException {
        String threadTs = "1700000000.000100";

        slackWiremock.stubFor(post("/api/conversations.history")
                .withName("export flow: channel history with a checked-off thread")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "ok": true,
                                    "messages": [
                                        {
                                            "type": "message",
                                            "text": "How do I configure a new ingress?",
                                            "ts": "%s",
                                            "reactions": [{"name": "white_check_mark", "count": 1, "users": ["U1"]}]
                                        }
                                    ],
                                    "has_more": false
                                }
                                """.formatted(threadTs))));

        slackWiremock.stubFor(post("/api/conversations.replies")
                .withName("export flow: thread replies for the checked-off thread")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "ok": true,
                                    "messages": [
                                        {"type": "message", "text": "How do I configure a new ingress?", "ts": "%s"},
                                        {"type": "message", "text": "Docs are at go/ingress-howto", "ts": "1700000001.000200"}
                                    ],
                                    "has_more": false
                                }
                                """.formatted(threadTs))));

        try {
            assertThat(supportBotClient.export().startStatusCode(1)).isEqualTo(202);

            Awaitility.await()
                    .atMost(Duration.ofSeconds(30))
                    .pollInterval(Duration.ofSeconds(1))
                    .untilAsserted(() -> {
                        var status = supportBotClient.export().status();
                        assertThat(status.running()).isFalse();
                        assertThat(status.error()).isNull();
                    });

            var readyStatus = supportBotClient.export().status();
            assertThat(readyStatus.ready()).isTrue();
            assertThat(readyStatus.threadCount()).isEqualTo(1);
            assertThat(readyStatus.filename()).startsWith("downloaded-threads-").endsWith(".zip");

            byte[] zip = supportBotClient.export().downloadBytes();
            List<String> entryNames = new ArrayList<>();
            String entryContent = null;
            try (ZipInputStream zipIn = new ZipInputStream(new ByteArrayInputStream(zip))) {
                ZipEntry entry;
                while ((entry = zipIn.getNextEntry()) != null) {
                    entryNames.add(entry.getName());
                    entryContent = new String(zipIn.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
            assertThat(entryNames).containsExactly(threadTs + ".txt");
            assertThat(entryContent).contains("How do I configure a new ingress?", "Docs are at go/ingress-howto");

            // Single-consumption, end-to-end through the real HTTP + in-memory store: a second
            // download after a successful one finds nothing left to serve.
            assertThat(supportBotClient.export().downloadStatusCode()).isEqualTo(404);
        } finally {
            // Best-effort drain: if an earlier assertion in this test failed before reaching the
            // consumption check above, a completed export could otherwise be left sitting in the
            // real, shared in-memory store — contaminating whichever test runs next (this service
            // instance stays up across multiple test runs, so it's not just this JVM at risk).
            supportBotClient.export().downloadStatusCode();
            slackWiremock.cleanupTestStubs();
        }
    }
}
