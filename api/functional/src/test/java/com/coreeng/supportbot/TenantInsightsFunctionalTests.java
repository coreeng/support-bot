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

    private void createNoSlaPr(long ticketId, String repo, int prNumber, Instant createdAt, String owningTeam) {
        supportBotClient
                .test()
                .createPrTrackingRecord(SupportBotClient.PrTrackingToCreate.builder()
                        .ticketId(ticketId)
                        .githubRepo(repo)
                        .prNumber(prNumber)
                        .prCreatedAt(createdAt)
                        .slaDeadline(null)
                        .owningTeam(owningTeam)
                        .build());
    }

    @Test
    public void insightsHasSlaReflectsPersistedColumnAcrossRepos() {
        // given — one repo with an SLA'd PR, one repo with only a no-SLA PR
        long ticketId = createTicket();
        Instant recent = Instant.now().minus(Duration.ofHours(1));
        createPr(ticketId, "test-org/pr-insights-has-sla-true", 9001, recent, "platform");
        createNoSlaPr(ticketId, "test-org/pr-insights-has-sla-false", 9002, recent, "platform");

        // when
        List<RepoInsights> results = getAllTimeStats();

        // then — has_sla is BOOL_OR across each repo's rows
        assertThat(results)
                .filteredOn(r -> r.repo().equals("test-org/pr-insights-has-sla-true"))
                .singleElement()
                .satisfies(r -> assertThat(r.hasSla()).isTrue());
        assertThat(results)
                .filteredOn(r -> r.repo().equals("test-org/pr-insights-has-sla-false"))
                .singleElement()
                .satisfies(r -> assertThat(r.hasSla()).isFalse());
    }

    @Test
    public void insightsHasSlaStaysTrueForAllClosedRepo() {
        // Motivating scenario for V15__pr_tracking_has_sla.sql — the reason this column exists.
        // Every PR in a repo has been closed (which nulls sla_deadline per updateStatus), so any
        // derivation like BOOL_OR(sla_deadline IS NOT NULL) would report has_sla=false and the
        // tenant-insights tab would misclassify a correctly-configured SLA repo as no-SLA.
        // The persisted has_sla column must survive the close.
        long ticketId = createTicket();
        Instant recent = Instant.now().minus(Duration.ofHours(1));

        SupportBotClient.PrTrackingRecordResponse pr1 = supportBotClient
                .test()
                .createPrTrackingRecord(SupportBotClient.PrTrackingToCreate.builder()
                        .ticketId(ticketId)
                        .githubRepo("test-org/pr-insights-all-closed")
                        .prNumber(9201)
                        .prCreatedAt(recent)
                        .slaDeadline(recent.plus(Duration.ofHours(24)))
                        .owningTeam("platform")
                        .build());
        SupportBotClient.PrTrackingRecordResponse pr2 = supportBotClient
                .test()
                .createPrTrackingRecord(SupportBotClient.PrTrackingToCreate.builder()
                        .ticketId(ticketId)
                        .githubRepo("test-org/pr-insights-all-closed")
                        .prNumber(9202)
                        .prCreatedAt(recent)
                        .slaDeadline(recent.plus(Duration.ofHours(24)))
                        .owningTeam("platform")
                        .build());

        // when — close every PR (real write path; nulls sla_deadline, leaves has_sla untouched)
        SupportBotClient.PrTrackingRecordResponse closed1 =
                supportBotClient.test().closePrTrackingRecord(pr1.id());
        SupportBotClient.PrTrackingRecordResponse closed2 =
                supportBotClient.test().closePrTrackingRecord(pr2.id());
        assertThat(closed1.status()).isEqualTo("CLOSED");
        assertThat(closed2.status()).isEqualTo("CLOSED");
        assertThat(closed1.slaDeadline()).isNull();
        assertThat(closed2.slaDeadline()).isNull();

        // then — the repo still reports hasSla=true because the column is persisted at insert
        List<RepoInsights> results = getAllTimeStats();
        assertThat(results)
                .filteredOn(r -> r.repo().equals("test-org/pr-insights-all-closed"))
                .singleElement()
                .satisfies(r -> {
                    assertThat(r.hasSla())
                            .as("closing every PR in an SLA-configured repo must preserve hasSla=true")
                            .isTrue();
                    assertThat(r.prCount()).isEqualTo(2);
                    assertThat(r.openCount()).isZero();
                });
    }

    @Test
    public void insightsHasSlaStaysTrueWhenRepoMixesSlaAndNoSlaPrs() {
        // given — same repo has one SLA'd and one no-SLA PR (the scenario BOOL_OR exists to
        // handle: a single no-SLA row would misclassify the whole repo without the aggregate)
        long ticketId = createTicket();
        Instant recent = Instant.now().minus(Duration.ofHours(1));
        createNoSlaPr(ticketId, "test-org/pr-insights-mixed", 9101, recent, "platform");
        createPr(ticketId, "test-org/pr-insights-mixed", 9102, recent, "platform");

        // when
        List<RepoInsights> results = getAllTimeStats();

        // then
        assertThat(results)
                .filteredOn(r -> r.repo().equals("test-org/pr-insights-mixed"))
                .singleElement()
                .satisfies(r -> {
                    assertThat(r.hasSla()).isTrue();
                    assertThat(r.prCount()).isEqualTo(2);
                });
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

        escalateTicket(botTicket, "wow", "bot");
        escalateTicket(manualTicket, "wow", "manual");

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
        escalateTicket(ticketId, "wow", "bot");

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
            double p50Seconds,
            double p90Seconds,
            double p99Seconds,
            boolean hasSla) {}

    public record EscalationBreakdown(long totalPrTickets, long botEscalatedTickets, long manuallyEscalatedTickets) {}
}
