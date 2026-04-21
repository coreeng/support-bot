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
import org.jspecify.annotations.Nullable;
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
    public void insightsHasSlaIsTrueWhenRepoCurrentlyInSlaConfig() {
        // given — a repo currently in SLA config (test-org/pr-test-repo ships in the
        // functional-tests profile with sla.default=PT24H) whose stored rows happen to all have
        // has_sla=false. Models the pre-V15 closed-row gap: stored signal is lost, current config
        // rules.
        long ticketId = createTicket();
        Instant recent = Instant.now().minus(Duration.ofHours(1));
        createNoSlaPr(ticketId, "test-org/pr-test-repo", 9301, recent, "wow");
        createNoSlaPr(ticketId, "test-org/pr-test-repo", 9302, recent, "wow");

        // when
        List<RepoInsights> results = getAllTimeStats();

        // then — config-driven hasSla=true despite zero SLA-marked rows in the DB
        assertThat(results)
                .filteredOn(r -> r.repo().equals("test-org/pr-test-repo"))
                .singleElement()
                .satisfies(r -> {
                    assertThat(r.hasSla())
                            .as("SLA-configured repo must report hasSla=true regardless of stored per-row signal")
                            .isTrue();
                    assertThat(r.prCount()).isEqualTo(2);
                });
    }

    @Test
    public void insightsHasSlaIsFalseWhenRepoIsNotInConfig() {
        // given — a repo absent from functional-tests config; stored rows are has_sla=false
        long ticketId = createTicket();
        Instant recent = Instant.now().minus(Duration.ofHours(1));
        createNoSlaPr(ticketId, "test-org/pr-insights-unconfigured", 9401, recent, "platform");

        // when
        List<RepoInsights> results = getAllTimeStats();

        // then — not in config → hasSla=false
        assertThat(results)
                .filteredOn(r -> r.repo().equals("test-org/pr-insights-unconfigured"))
                .singleElement()
                .satisfies(r -> assertThat(r.hasSla()).isFalse());
    }

    @Test
    public void insightsHasSlaIsFalseForRepoNotInConfigEvenWhenStoredRowsAreSlaMarked() {
        // given — a repo absent from config but with SLA'd rows in the DB (simulates a repo
        // reconfigured away from SLA, or fully removed from config after historical tracking)
        long ticketId = createTicket();
        Instant recent = Instant.now().minus(Duration.ofHours(1));
        createPr(ticketId, "test-org/pr-insights-reconfigured-away", 9501, recent, "platform");
        createPr(ticketId, "test-org/pr-insights-reconfigured-away", 9502, recent, "platform");

        // when
        List<RepoInsights> results = getAllTimeStats();

        // then — stored SLA signal is ignored; present-day config is authoritative
        assertThat(results)
                .filteredOn(r -> r.repo().equals("test-org/pr-insights-reconfigured-away"))
                .singleElement()
                .satisfies(r -> {
                    assertThat(r.hasSla())
                            .as("repo not in current config must report hasSla=false even with SLA rows"
                                    + " in the DB — badge reflects present state, not history")
                            .isFalse();
                    assertThat(r.prCount()).isEqualTo(2);
                });
    }

    @Test
    public void insightsHasSlaStaysTrueForAllClosedConfiguredRepo() {
        // Documents that the badge on an SLA-configured repo survives closing every PR, even
        // though close nulls sla_deadline. The DB-side has_sla column still holds per-row truth,
        // but the dashboard ignores it — what matters is that config still says SLA. This
        // exercises the full close → read path end-to-end for an in-config repo.
        long ticketId = createTicket();
        Instant recent = Instant.now().minus(Duration.ofHours(1));

        SupportBotClient.PrTrackingRecordResponse pr1 = supportBotClient
                .test()
                .createPrTrackingRecord(SupportBotClient.PrTrackingToCreate.builder()
                        .ticketId(ticketId)
                        .githubRepo("test-org/pr-test-repo")
                        .prNumber(9201)
                        .prCreatedAt(recent)
                        .slaDeadline(recent.plus(Duration.ofHours(24)))
                        .owningTeam("wow")
                        .build());
        SupportBotClient.PrTrackingRecordResponse pr2 = supportBotClient
                .test()
                .createPrTrackingRecord(SupportBotClient.PrTrackingToCreate.builder()
                        .ticketId(ticketId)
                        .githubRepo("test-org/pr-test-repo")
                        .prNumber(9202)
                        .prCreatedAt(recent)
                        .slaDeadline(recent.plus(Duration.ofHours(24)))
                        .owningTeam("wow")
                        .build());

        // when — close every PR (real write path; nulls sla_deadline)
        SupportBotClient.PrTrackingRecordResponse closed1 =
                supportBotClient.test().closePrTrackingRecord(pr1.id());
        SupportBotClient.PrTrackingRecordResponse closed2 =
                supportBotClient.test().closePrTrackingRecord(pr2.id());
        assertThat(closed1.status()).isEqualTo("CLOSED");
        assertThat(closed2.status()).isEqualTo("CLOSED");
        assertThat(closed1.slaDeadline()).isNull();
        assertThat(closed2.slaDeadline()).isNull();

        // then — still hasSla=true because test-org/pr-test-repo remains SLA-configured
        List<RepoInsights> results = getAllTimeStats();
        assertThat(results)
                .filteredOn(r -> r.repo().equals("test-org/pr-test-repo"))
                .singleElement()
                .satisfies(r -> {
                    assertThat(r.hasSla())
                            .as("closing every PR in an SLA-configured repo must not affect the badge")
                            .isTrue();
                    assertThat(r.prCount()).isEqualTo(2);
                    assertThat(r.openCount()).isZero();
                });
    }

    @Test
    public void inFlightPrs_hasSlaRoundTripsDerivationFromSlaDeadline() {
        // End-to-end coverage for the /in-flight-prs hasSla column: NewPrTracking derives hasSla
        // from slaDeadline != null at insert time; the value is then stored in pr_tracking.has_sla
        // (V15) and read back through findAllInFlight's DISTINCT ON query. A regression at any
        // point in that chain (derivation, insert set(), SELECT projection, Boolean type mapping,
        // or the null-coerce fix in JdbcPrTrackingRepository) would silently flip the badge.
        long ticketId = createTicket();
        Instant recent = Instant.now().minus(Duration.ofHours(1));
        createPr(ticketId, "test-org/pr-inflight-sla", 7001, recent, "platform");
        createNoSlaPr(ticketId, "test-org/pr-inflight-nosla", 7002, recent, "platform");

        // when
        List<InFlightPrResponse> inFlight = getInFlightPrs();

        // then — each PR carries the hasSla its construction implied
        assertThat(inFlight)
                .filteredOn(p -> p.githubRepo().equals("test-org/pr-inflight-sla") && p.prNumber() == 7001)
                .singleElement()
                .satisfies(p -> {
                    assertThat(p.hasSla())
                            .as("PR created with an slaDeadline must round-trip hasSla=true")
                            .isTrue();
                    assertThat(p.slaDeadline()).isNotNull();
                });
        assertThat(inFlight)
                .filteredOn(p -> p.githubRepo().equals("test-org/pr-inflight-nosla") && p.prNumber() == 7002)
                .singleElement()
                .satisfies(p -> {
                    assertThat(p.hasSla())
                            .as("PR created with null slaDeadline must round-trip hasSla=false")
                            .isFalse();
                    assertThat(p.slaDeadline()).isNull();
                });
    }

    private List<InFlightPrResponse> getInFlightPrs() {
        return given().get(config.supportBot().baseUrl() + "/tenant-insights/in-flight-prs")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList(".", InFlightPrResponse.class);
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

    public record InFlightPrResponse(
            String githubRepo,
            int prNumber,
            String prUrl,
            String status,
            String waitingOn,
            Instant prCreatedAt,
            @Nullable Instant slaDeadline,
            @Nullable Long slaRemainingSeconds,
            @Nullable Instant lastReviewAt,
            String owningTeam,
            String owningTeamLabel,
            String ticketChannelId,
            String ticketQueryTs,
            @Nullable Instant escalatedAt,
            boolean hasSla) {}
}
