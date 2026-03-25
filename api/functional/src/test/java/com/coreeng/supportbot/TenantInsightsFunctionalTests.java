package com.coreeng.supportbot;

import static com.coreeng.supportbot.testkit.UserRole.support;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import com.coreeng.supportbot.testkit.Config;
import com.coreeng.supportbot.testkit.SupportBotClient;
import com.coreeng.supportbot.testkit.Ticket;
import com.coreeng.supportbot.testkit.TestKit;
import com.coreeng.supportbot.testkit.TestKitExtension;
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
        List<RepoInsights> results = getStats(LocalDate.now().minusDays(30), LocalDate.now().plusDays(1));

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
        assertThat(results).extracting(RepoInsights::repo)
                .contains("test-org/pr-insights-networking", "test-org/pr-insights-storage");
    }

    @Test
    public void returnsEmptyWhenNoDataInRange() {
        // given — a PR created 200 days ago
        long ticketId = createTicket();
        createPr(ticketId, "test-org/pr-insights-networking", 1, Instant.now().minus(Duration.ofDays(200)), "platform");

        // when — querying the last 7 days
        List<RepoInsights> results = getStats(LocalDate.now().minusDays(7), LocalDate.now().plusDays(1));

        // then — empty list, not an error
        assertThat(results).isEmpty();
    }

    private List<RepoInsights> getStats(LocalDate dateFrom, LocalDate dateTo) {
        return given()
                .queryParam("dateFrom", dateFrom.toString())
                .queryParam("dateTo", dateTo.toString())
                .get(config.supportBot().baseUrl() + "/tenant-insights/pr-stats")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList(".", RepoInsights.class);
    }

    private List<RepoInsights> getAllTimeStats() {
        return given()
                .get(config.supportBot().baseUrl() + "/tenant-insights/pr-stats")
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
        supportBotClient.test()
                .createPrTrackingRecord(SupportBotClient.PrTrackingToCreate.builder()
                        .ticketId(ticketId)
                        .githubRepo(repo)
                        .prNumber(prNumber)
                        .prCreatedAt(createdAt)
                        .slaDeadline(createdAt.plus(Duration.ofHours(24)))
                        .owningTeam(owningTeam)
                        .build());
    }

    public record RepoInsights(
            String repo, String owningTeam,
            long prCount, long openCount, long escalatedCount, long breachedCount,
            double p50Seconds, double p90Seconds, double p99Seconds) {}
}
