package com.coreeng.supportbot;

import static com.coreeng.supportbot.testkit.UserRole.support;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import com.coreeng.supportbot.testkit.Config;
import com.coreeng.supportbot.testkit.MessageTs;
import com.coreeng.supportbot.testkit.SupportBotClient;
import com.coreeng.supportbot.testkit.TestKit;
import com.coreeng.supportbot.testkit.TestKitExtension;
import com.coreeng.supportbot.testkit.Ticket;
import com.google.common.collect.ImmutableList;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Functional tests for the /tenant-insights/pr-stats endpoint.
 * Seeds PR tracking data via test helpers and verifies the SQL query
 * (percentile_cont, FILTER, date filtering) against a real database.
 */
@ExtendWith(TestKitExtension.class)
public class TenantInsightsFunctionalTests {

    private static final String ESCALATION_SOURCE_BOT = "bot";
    private static final String ESCALATION_SOURCE_MANUAL = "manual";

    private TestKit testKit;
    private Config config;
    private SupportBotClient supportBotClient;

    @BeforeEach
    void cleanup() {
        supportBotClient.test().cleanupPrTrackingRecords();
    }

    @Test
    public void returnsPerRepoInsightsFilteredByDateRange() {
        // given — two PRs in networking (10 days old) and one in storage (60 days old)
        long ticketId = createTicket();
        Instant tenDaysAgo = Instant.now().minus(Duration.ofDays(10));
        Instant sixtyDaysAgo = Instant.now().minus(Duration.ofDays(60));

        createPr(ticketId, "test-org/pr-insights-networking", 1, tenDaysAgo, "platform");
        createPr(ticketId, "test-org/pr-insights-networking", 2, tenDaysAgo, "platform");
        createPr(ticketId, "test-org/pr-insights-storage", 1, sixtyDaysAgo, "infra");

        // when — querying the last 30 days
        List<RepoInsights> results =
                getStats(LocalDate.now().minusDays(30), LocalDate.now().plusDays(1));

        // then — only networking PRs appear
        assertThat(results).hasSize(1);
        assertThat(results.get(0).repo()).isEqualTo("test-org/pr-insights-networking");
        assertThat(results.get(0).owningTeam()).isEqualTo("platform");
        assertThat(results.get(0).prCount()).isEqualTo(2);
        assertThat(results.get(0).openCount()).isEqualTo(2);
        assertThat(results.get(0).p50Seconds()).isGreaterThan(0);
    }

    @Test
    public void returnsAllReposWhenNoDatesProvided() {
        // given — PRs in two repos at different ages
        long ticketId = createTicket();
        createPr(ticketId, "test-org/pr-insights-networking", 1, Instant.now().minus(Duration.ofDays(5)), "platform");
        createPr(ticketId, "test-org/pr-insights-storage", 1, Instant.now().minus(Duration.ofDays(100)), "infra");

        // when — querying without date params
        List<RepoInsights> results = getAllTimeStats();

        // then — both repos returned
        assertThat(results)
                .extracting(RepoInsights::repo)
                .contains("test-org/pr-insights-networking", "test-org/pr-insights-storage");
    }

    @Test
    public void returnsPerRepoEscalationCounts() {
        // given — two tickets: one with bot escalation, one with manual
        String repoName = "test-org/pr-insights-esc-counts";
        long botTicket = createTicket();
        long manualTicket = createTicket();
        Instant fiveDaysAgo = Instant.now().minus(Duration.ofDays(5));

        createPr(botTicket, repoName, 901, fiveDaysAgo, "platform");
        createPr(manualTicket, repoName, 902, fiveDaysAgo, "platform");

        escalateTicket(botTicket, "platform", ESCALATION_SOURCE_BOT);
        escalateTicket(manualTicket, "platform", ESCALATION_SOURCE_MANUAL);

        // when — querying all-time
        List<RepoInsights> results = getAllTimeStats();

        // then — per-repo counts reflect escalation sources
        assertThat(results).extracting(RepoInsights::repo).contains(repoName);
        RepoInsights repo = results.stream()
                .filter(r -> r.repo().equals(repoName))
                .findFirst()
                .orElseThrow();
        assertThat(repo.botEscalatedCount()).isEqualTo(1);
        assertThat(repo.manualEscalatedCount()).isEqualTo(1);
    }

    @Test
    public void returnsEmptyWhenNoDataInRange() {
        // given — a PR created 200 days ago
        long ticketId = createTicket();
        createPr(ticketId, "test-org/pr-insights-networking", 1, Instant.now().minus(Duration.ofDays(200)), "platform");

        // when — querying the last 7 days
        List<RepoInsights> results =
                getStats(LocalDate.now().minusDays(7), LocalDate.now().plusDays(1));

        // then — empty list, not an error
        assertThat(results).isEmpty();
    }

    private List<RepoInsights> getStats(LocalDate dateFrom, LocalDate dateTo) {
        return given().queryParam("dateFrom", dateFrom.toString())
                .queryParam("dateTo", dateTo.toString())
                .get(config.supportBot().baseUrl() + "/tenant-insights/pr-stats")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList(".", RepoInsights.class);
    }

    private List<RepoInsights> getAllTimeStats() {
        return given().get(config.supportBot().baseUrl() + "/tenant-insights/pr-stats")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList(".", RepoInsights.class);
    }

    private long createTicket() {
        Ticket ticket = testKit.as(support).ticket().create(builder -> builder.message("PR insights test"));
        return ticket.id();
    }

    private void createPr(long ticketId, String repo, int prNumber, Instant createdAt, String owningTeam) {
        supportBotClient
                .test()
                .createPrTrackingRecord(SupportBotClient.PrTrackingToCreate.builder()
                        .ticketId(ticketId)
                        .githubRepo(repo)
                        .prNumber(prNumber)
                        .prCreatedAt(createdAt)
                        .slaDeadline(createdAt.plus(Duration.ofHours(24)))
                        .owningTeam(owningTeam)
                        .build());
    }

    @Test
    public void escalationBreakdown_countsBotAndManualSources() {
        // given — three PR tickets: one with bot escalation, one with manual, one with none
        long botTicket = createTicket();
        long manualTicket = createTicket();
        long noEscTicket = createTicket();

        Instant prCreatedAt = Instant.now().minus(Duration.ofHours(1));
        createPr(botTicket, "test-org/pr-insights-esc", 801, prCreatedAt, "wow");
        createPr(manualTicket, "test-org/pr-insights-esc", 802, prCreatedAt, "wow");
        createPr(noEscTicket, "test-org/pr-insights-esc", 803, prCreatedAt, "wow");

        escalateTicket(botTicket, "wow", ESCALATION_SOURCE_BOT);
        escalateTicket(manualTicket, "wow", ESCALATION_SOURCE_MANUAL);

        // when
        var response = getEscalationBreakdown(null, null);

        // then — at least our seeded data is counted correctly
        assertThat(response.totalPrTickets()).isGreaterThanOrEqualTo(3);
        assertThat(response.botEscalatedTickets()).isGreaterThanOrEqualTo(1);
        assertThat(response.manuallyEscalatedTickets()).isGreaterThanOrEqualTo(1);
    }

    @Test
    public void escalationBreakdown_returnsZerosWhenNoDataInRange() {
        // given — a PR ticket with bot escalation created now
        long ticketId = createTicket();
        createPr(ticketId, "test-org/pr-insights-esc", 810, Instant.now().minus(Duration.ofHours(1)), "wow");
        escalateTicket(ticketId, "wow", ESCALATION_SOURCE_BOT);

        // when — querying a future date range
        var response = getEscalationBreakdown(LocalDate.of(2099, 1, 1), LocalDate.of(2099, 12, 31));

        // then — nothing matches
        assertThat(response.totalPrTickets()).isEqualTo(0);
        assertThat(response.botEscalatedTickets()).isEqualTo(0);
        assertThat(response.manuallyEscalatedTickets()).isEqualTo(0);
    }

    private EscalationBreakdown getEscalationBreakdown(LocalDate dateFrom, LocalDate dateTo) {
        var request = given();
        if (dateFrom != null) {
            request = request.queryParam("dateFrom", dateFrom.toString());
        }
        if (dateTo != null) {
            request = request.queryParam("dateTo", dateTo.toString());
        }
        return request.get(config.supportBot().baseUrl() + "/tenant-insights/escalation-breakdown")
                .then()
                .statusCode(200)
                .extract()
                .as(EscalationBreakdown.class);
    }

    private void escalateTicket(long ticketId, String team, String source) {
        supportBotClient
                .test()
                .escalateTicket(SupportBotClient.EscalationToCreate.builder()
                        .ticketId(ticketId)
                        .team(team)
                        .createdMessageTs(MessageTs.now())
                        .tags(ImmutableList.of())
                        .source(source)
                        .build());
    }

    public record RepoInsights(
            String repo,
            String owningTeam,
            long prCount,
            long openCount,
            long escalatedCount,
            long breachedCount,
            long botEscalatedCount,
            long manualEscalatedCount,
            double p50Seconds,
            double p90Seconds,
            double p99Seconds) {}

    public record EscalationBreakdown(long totalPrTickets, long botEscalatedTickets, long manuallyEscalatedTickets) {}
}
