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
import io.restassured.filter.log.LogDetail;
import io.restassured.response.Response;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.ThreadLocalRandom;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Exercises the Support Summary endpoints against a booted app: the real Spring Security rules for
 * {@code /summary}, {@code /summary/prompt}, {@code /summary/team-groups} and {@code /summary/enabled},
 * the window and team group validation, the seeded summary prompt, and the visit-triggered refresh
 * (generating → ready) that runs the backfill and the prose generation through the WireMock LLM proxy
 * stub — for the whole window and for a team group.
 */
@ExtendWith(TestKitExtension.class)
public class SummaryApiTests {

    private static final String TEST_BYPASS_USER = "test@functional.test";
    private static final String SUPPORT_ENGINEER_ROLE = "support";
    private static final String LEADERSHIP_ROLE = "leadership";

    // Not "leadership": TestAuthBypassFilter grants it SUPPORT_ENGINEER too, so it can't represent
    // a principal outside the page's roles. "escalation" has neither.
    private static final String NON_SUMMARY_ROLE = "escalation";

    // A fixed window in the past: it never contains tickets seeded by other tests, so the figures
    // below are deterministic whatever else the suite has created.
    private static final String FIXED_WINDOW = "?from=2024-01-01&to=2024-01-14";

    // First line of the V38 seed for the in-use `summary` prompt.
    private static final String SEEDED_SUMMARY_PROMPT_TITLE = "Platform Support Demand Summary Prompt";

    // The prose the permanent generateContent stub (SlackWiremock) answers with. Seeing it in the
    // ready summary proves the text travelled through the proxy ChatModel, not a fallback.
    private static final String STUBBED_LLM_TEXT = "Functional test analysis result";

    // Configured in application-functionaltests.yaml.
    private static final String TEAM_GROUP = "functional-area";

    private Config config;
    private SupportBotClient supportBotClient;
    private TestKit testKit;
    private SlackWiremock slackWiremock;

    @Test
    void summaryEnabled_returnsTrue_forAnyAuthenticatedRole() {
        // The sidebar asks this for every user, so it is open to roles the page itself rejects.
        var response = get(NON_SUMMARY_ROLE, "/summary/enabled");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.jsonPath().getBoolean("enabled")).isTrue();
    }

    @Test
    void summary_returns200WithBreakdowns_forSupportEngineer() {
        var response = get(SUPPORT_ENGINEER_ROLE, "/summary" + FIXED_WINDOW);

        assertSummaryPayload(response);
    }

    @Test
    void summary_returns200WithBreakdowns_forLeadership() {
        // Leadership must be able to load the page (and so trigger the backfill) without holding the
        // SUPPORT_ENGINEER-only /analysis/run permission.
        var response = get(LEADERSHIP_ROLE, "/summary" + FIXED_WINDOW);

        assertSummaryPayload(response);
    }

    @Test
    void summary_returns403_forNonSummaryRole() {
        assertThat(supportBotClient.getStatusCodeAsRole("/summary" + FIXED_WINDOW, NON_SUMMARY_ROLE))
                .isEqualTo(403);
    }

    @Test
    void summary_returns400_whenToIsBeforeFrom() {
        var response = get(SUPPORT_ENGINEER_ROLE, "/summary?from=2024-01-14&to=2024-01-01");

        assertInvalidWindow(response);
        assertThat(response.jsonPath().getString("detail")).contains("must not be before");
    }

    @Test
    void summary_returns400_whenWindowExceeds92Days() {
        // 2023-01-01..2023-04-03 is 93 days with both ends included; 92 (one quarter) is the widest allowed.
        var response = get(SUPPORT_ENGINEER_ROLE, "/summary?from=2023-01-01&to=2023-04-03");

        assertInvalidWindow(response);
        assertThat(response.jsonPath().getString("detail")).contains("92");
    }

    @Test
    void summaryPrompt_returnsSeededSummaryPrompt_forSupportEngineer() {
        var response = get(SUPPORT_ENGINEER_ROLE, "/summary/prompt");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.jsonPath().getString("prompt")).contains(SEEDED_SUMMARY_PROMPT_TITLE);
    }

    @Test
    void summaryPrompt_returnsSeededSummaryPrompt_forLeadership() {
        var response = get(LEADERSHIP_ROLE, "/summary/prompt");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.jsonPath().getString("prompt")).contains(SEEDED_SUMMARY_PROMPT_TITLE);
    }

    @Test
    void summaryPrompt_returns403_forNonSummaryRole() {
        // /summary/prompt has a scoped AccessDeniedHandler that writes a JSON body with the 403.
        assertThat(supportBotClient.getStatusCodeAsRole("/summary/prompt", NON_SUMMARY_ROLE))
                .isEqualTo(403);
    }

    @Test
    void summary_startsGenerating_onFirstVisit_andReachesReady() {
        // A window no earlier run can have cached: the service (and its DB) stays up across test
        // runs, so a fixed window would already be `ready` on the second run. Snapshots are keyed on
        // (from, to, prompt), and the far past holds no tickets, so the only side effect is one
        // snapshot row per run.
        String window = freshPastWindow();

        // First visit: nothing cached, so the refresh is started server-side and reported as
        // generating. Another run may already hold the global analysis lock — that is also
        // reported as generating and converges once it finishes, so the assertion holds either way.
        var first = get(SUPPORT_ENGINEER_ROLE, "/summary" + window);
        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(first.jsonPath().getString("summary.state")).isEqualTo("generating");
        assertThat(first.jsonPath().getString("summary.progress.phase")).isIn("classifying", "summarising");
        assertThat(first.jsonPath().getString("summary.content")).isNull();

        // Then: no tickets to classify, and the proxy stub answers the prose request, so the run ends
        // in a stored snapshot the next poll serves as ready.
        Awaitility.await()
                .atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(() -> {
                    var polled = get(SUPPORT_ENGINEER_ROLE, "/summary" + window);
                    assertThat(polled.statusCode()).isEqualTo(200);
                    assertThat(polled.jsonPath().getString("summary.state"))
                            .as("summary section: %s", polled.jsonPath().getMap("summary"))
                            .isEqualTo("ready");
                });

        var ready = get(SUPPORT_ENGINEER_ROLE, "/summary" + window);
        assertThat(ready.jsonPath().getString("summary.state")).isEqualTo("ready");
        assertThat(ready.jsonPath().getString("summary.content")).contains(STUBBED_LLM_TEXT);
        assertThat(ready.jsonPath().getString("summary.model")).isNotBlank();
        assertThat(ready.jsonPath().getString("summary.generatedAt")).isNotBlank();
        assertThat(ready.jsonPath().getString("summary.error")).isNull();
        assertThat(ready.jsonPath().getString("summary.progress")).isNull();
    }

    @Test
    void teamGroups_listsTheConfiguredGroupsWithTheirTeams_forSupportEngineerAndLeadership() {
        for (String role : new String[] {SUPPORT_ENGINEER_ROLE, LEADERSHIP_ROLE}) {
            var response = get(role, "/summary/team-groups");

            assertThat(response.statusCode()).isEqualTo(200);
            var body = response.jsonPath();
            assertThat(body.getString("displayName")).isEqualTo("Area");
            assertThat(body.getList("groups.code", String.class)).containsExactly(TEAM_GROUP);
            assertThat(body.getString("groups[0].label")).isEqualTo("Functional Area");
            assertThat(body.getList("groups[0].teams.code", String.class))
                    .containsExactlyInAnyOrder("wow", "connected-app");
        }
    }

    @Test
    void teamGroups_returns403_forNonSummaryRole() {
        assertThat(supportBotClient.getStatusCodeAsRole("/summary/team-groups", NON_SUMMARY_ROLE))
                .isEqualTo(403);
    }

    @Test
    void summary_returns400_forAnUnknownTeamGroup() {
        var response = get(SUPPORT_ENGINEER_ROLE, "/summary" + FIXED_WINDOW + "&teamGroup=no-such-area");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.jsonPath().getString("code")).isEqualTo("SUMMARY_TEAM_GROUP_UNKNOWN");
        assertThat(response.jsonPath().getString("title")).isEqualTo("Unknown team group");
    }

    @Test
    void summary_isNarrowedToTheTeamGroup_andEchoesIt() {
        var response = get(LEADERSHIP_ROLE, "/summary" + FIXED_WINDOW + "&teamGroup=" + TEAM_GROUP);

        assertSummaryPayload(response);
        assertThat(response.jsonPath().getString("teamGroup.code")).isEqualTo(TEAM_GROUP);
        assertThat(response.jsonPath().getString("teamGroup.label")).isEqualTo("Functional Area");
    }

    @Test
    void summary_teamGroupBreakdown_onlyContainsTheGroupsTeams() {
        // One ticket inside the group and one outside it, so the narrowing has something to drop and
        // something to keep. The window also holds whatever tickets other tests have raised.
        closeTicketFor("wow");
        closeTicketFor("infra-integration");
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        String allPath = "/summary?from=" + today.minusDays(30) + "&to=" + today;
        String groupPath = allPath + "&teamGroup=" + TEAM_GROUP;

        // Opening a window that ends today starts a refresh, which classifies the window's closed
        // tickets and so fetches their Slack threads. Stub that, and let the refreshes finish inside
        // the test, so no fetch lands after the stub is gone.
        slackWiremock.stubFor(post("/api/conversations.replies")
                .withName("summary team group: thread replies for the window's closed tickets")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "ok": true,
                                    "messages": [
                                        {"type": "message", "text": "Summary team group query", "ts": "1700000000.000100"}
                                    ],
                                    "has_more": false
                                }
                                """)));
        try {
            var all = get(SUPPORT_ENGINEER_ROLE, allPath);
            var group = get(SUPPORT_ENGINEER_ROLE, groupPath);

            assertThat(all.statusCode()).isEqualTo(200);
            assertThat(group.statusCode()).isEqualTo(200);
            assertThat(all.jsonPath().getList("teams.label", String.class)).contains("wow", "infra-integration");
            assertThat(group.jsonPath().getList("teams.label", String.class))
                    .contains("wow")
                    .isSubsetOf("wow", "connected-app");
            assertThat(group.jsonPath().getLong("totalTickets"))
                    .isPositive()
                    .isLessThan(all.jsonPath().getLong("totalTickets"));

            // The lock is global: the group's own refresh only starts once the whole window's ends.
            awaitSummarySettled(allPath);
            awaitSummarySettled(groupPath);
        } finally {
            slackWiremock.cleanupTestStubs();
        }
    }

    /** Polls until the summary for {@code path} is no longer generating, starting its refresh if it needs one. */
    private void awaitSummarySettled(String path) {
        Awaitility.await()
                .atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(() -> {
                    var polled = get(SUPPORT_ENGINEER_ROLE, path);
                    assertThat(polled.statusCode()).isEqualTo(200);
                    assertThat(polled.jsonPath().getString("summary.state"))
                            .as(
                                    "summary section for %s: %s",
                                    path, polled.jsonPath().getMap("summary"))
                            .isIn("ready", "unavailable");
                });
    }

    @Test
    void summary_forATeamGroup_reachesReadyIndependentlyOfTheWholeWindow() {
        // Its own fresh window, so the group's snapshot is the first for it whatever earlier runs left.
        String window = freshPastWindow() + "&teamGroup=" + TEAM_GROUP;

        Awaitility.await()
                .atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(() -> {
                    var polled = get(SUPPORT_ENGINEER_ROLE, "/summary" + window);
                    assertThat(polled.statusCode()).isEqualTo(200);
                    assertThat(polled.jsonPath().getString("summary.state"))
                            .as("summary section: %s", polled.jsonPath().getMap("summary"))
                            .isEqualTo("ready");
                });

        var ready = get(SUPPORT_ENGINEER_ROLE, "/summary" + window);
        assertThat(ready.jsonPath().getString("teamGroup.code")).isEqualTo(TEAM_GROUP);
        assertThat(ready.jsonPath().getString("summary.content")).contains(STUBBED_LLM_TEXT);
    }

    private void closeTicketFor(String team) {
        var ticket = testKit.as(tenant).ticket().create(builder -> builder.message("Summary team group query"));
        var closeStubs = ticket.stubCloseFlow("summary team group ticket closed");
        supportBotClient.updateTicket(
                ticket.id(),
                SupportBotClient.UpdateTicketRequest.builder()
                        .status("closed")
                        .authorsTeam(team)
                        .tags(ImmutableList.of("ingresses"))
                        .impact("productionBlocking")
                        .build());
        closeStubs.awaitAllCalled(Duration.ofSeconds(1));
    }

    private static void assertSummaryPayload(Response response) {
        assertThat(response.statusCode()).isEqualTo(200);
        var body = response.jsonPath();

        assertThat(body.getString("from")).isEqualTo("2024-01-01");
        assertThat(body.getString("to")).isEqualTo("2024-01-14");

        // The window predates every ticket the suite creates, so the counts are known exactly and
        // must reconcile: classified + unclassified == total.
        assertThat(body.getLong("totalTickets")).isZero();
        assertThat(body.getLong("classifiedTickets")).isZero();
        assertThat(body.getLong("unclassifiedTickets")).isZero();

        assertThat(body.getList("drivers")).isEmpty();
        assertThat(body.getList("categories")).isEmpty();
        assertThat(body.getList("knowledgeGaps")).isEmpty();
        assertThat(body.getList("features")).isEmpty();
        assertThat(body.getList("teams")).isEmpty();
        assertThat(body.getList("products")).isEmpty();

        // The prose section never blocks the breakdowns: whichever state the shared refresh is in,
        // the rest of the page is served.
        assertThat(body.getString("summary.state")).isIn("generating", "ready", "unavailable");
    }

    private static void assertInvalidWindow(Response response) {
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.jsonPath().getString("code")).isEqualTo("SUMMARY_WINDOW_INVALID");
        assertThat(response.jsonPath().getString("title")).isEqualTo("Invalid summary window");
    }

    /** A single day somewhere in 1990–2009, as a {@code ?from=&to=} query. */
    private static String freshPastWindow() {
        LocalDate day =
                LocalDate.of(1990, 1, 1).plusDays(ThreadLocalRandom.current().nextInt(20 * 365));
        return "?from=" + day + "&to=" + day;
    }

    // Requests are built the way the testkit's SupportBotClient builds them (TestAuthBypassFilter
    // headers); the client has no typed summary accessor, and the assertions here are on the raw
    // wire shape anyway.
    private Response get(String role, String path) {
        return given().header("X-Test-User", TEST_BYPASS_USER)
                .header("X-Test-Role", role)
                .when()
                .get(config.supportBot().baseUrl() + path)
                .then()
                .log()
                .ifValidationFails(LogDetail.ALL, true)
                .extract()
                .response();
    }
}
