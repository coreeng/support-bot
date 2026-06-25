package com.coreeng.supportbot;

import static com.coreeng.supportbot.testkit.UserRole.support;
import static org.assertj.core.api.Assertions.assertThat;

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
import org.junit.jupiter.api.AfterEach;
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
    private SupportBotClient supportBotClient;

    @BeforeEach
    void cleanup() {
        supportBotClient.test().cleanupPrTrackingRecords();
    }

    @AfterEach
    void cleanupAfter() {
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
        List<SupportBotClient.RepoInsightsResponse> results =
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
        List<SupportBotClient.RepoInsightsResponse> results = getAllTimeStats();

        // then — both repos returned
        assertThat(results)
                .extracting(SupportBotClient.RepoInsightsResponse::repo)
                .contains("test-org/pr-insights-networking", "test-org/pr-insights-storage");
    }

    @Test
    public void returnsEmptyWhenNoDataInRange() {
        // given — a PR created 200 days ago
        long ticketId = createTicket();
        createPr(ticketId, "test-org/pr-insights-networking", 1, Instant.now().minus(Duration.ofDays(200)), "platform");

        // when — querying the last 7 days
        List<SupportBotClient.RepoInsightsResponse> results =
                getStats(LocalDate.now().minusDays(7), LocalDate.now().plusDays(1));

        // then — empty list, not an error
        assertThat(results).isEmpty();
    }

    private List<SupportBotClient.RepoInsightsResponse> getStats(LocalDate dateFrom, LocalDate dateTo) {
        return supportBotClient.tenantInsights().prStats(dateFrom, dateTo).items();
    }

    private List<SupportBotClient.RepoInsightsResponse> getAllTimeStats() {
        return supportBotClient.tenantInsights().prStats().items();
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
        List<SupportBotClient.RepoInsightsResponse> results = getAllTimeStats();

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
        List<SupportBotClient.RepoInsightsResponse> results = getAllTimeStats();

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
        List<SupportBotClient.RepoInsightsResponse> results = getAllTimeStats();

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
        List<SupportBotClient.RepoInsightsResponse> results = getAllTimeStats();
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
        List<SupportBotClient.InFlightPrResponse> inFlight = getInFlightPrs();

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

    private List<SupportBotClient.InFlightPrResponse> getInFlightPrs() {
        return supportBotClient.tenantInsights().inFlightPrs().items();
    }

    // The funnel asserts use a baseline-delta approach: pr_tracking is wiped between tests (so PR
    // and intervention counts are already isolated to each test), but tickets and escalations
    // accumulate across the shared DB, so totalSupportTickets is asserted as a delta from a
    // baseline snapshot taken before seeding. This keeps every assertion exact and predictable.

    @Test
    public void requestBreakdown_nonPrTicketCountsInTotalButNotAsPr() {
        var before = getRequestBreakdown(null, null);

        createTicket(); // plain support ticket, no PR

        var after = getRequestBreakdown(null, null);
        assertThat(after.totalSupportTickets()).isEqualTo(before.totalSupportTickets() + 1);
        assertThat(after.totalPrTickets()).isEqualTo(before.totalPrTickets());
        assertThat(after.interventionPrTickets()).isEqualTo(before.interventionPrTickets());
    }

    @Test
    public void requestBreakdown_prTicketCountsInTotalAndAsPr() {
        var before = getRequestBreakdown(null, null);

        long ticket = createTicket();
        createPr(ticket, "test-org/pr-insights-req", 920, Instant.now().minus(Duration.ofHours(1)), "wow");

        var after = getRequestBreakdown(null, null);
        assertThat(after.totalSupportTickets()).isEqualTo(before.totalSupportTickets() + 1);
        assertThat(after.totalPrTickets()).isEqualTo(before.totalPrTickets() + 1);
        assertThat(after.interventionPrTickets()).isEqualTo(before.interventionPrTickets());
    }

    @Test
    public void requestBreakdown_ticketWithMultiplePrsCountedOnce() {
        var before = getRequestBreakdown(null, null);

        long ticket = createTicket();
        Instant prCreatedAt = Instant.now().minus(Duration.ofHours(1));
        createPr(ticket, "test-org/pr-insights-req", 921, prCreatedAt, "wow");
        createPr(ticket, "test-org/pr-insights-req", 922, prCreatedAt, "wow");

        // one ticket referencing two PRs is a single PR ticket (and a single support ticket)
        var after = getRequestBreakdown(null, null);
        assertThat(after.totalSupportTickets()).isEqualTo(before.totalSupportTickets() + 1);
        assertThat(after.totalPrTickets()).isEqualTo(before.totalPrTickets() + 1);
    }

    @Test
    public void requestBreakdown_manualEscalationOnPrTicketCountsAsIntervention() {
        var before = getRequestBreakdown(null, null);

        long ticket = createTicket();
        createPr(ticket, "test-org/pr-insights-req", 930, Instant.now().minus(Duration.ofHours(1)), "wow");
        escalateTicket(ticket, "wow", "manual");

        var after = getRequestBreakdown(null, null);
        assertThat(after.totalPrTickets()).isEqualTo(before.totalPrTickets() + 1);
        assertThat(after.interventionPrTickets()).isEqualTo(before.interventionPrTickets() + 1);
    }

    @Test
    public void requestBreakdown_botEscalationOnPrTicketDoesNotCountAsIntervention() {
        var before = getRequestBreakdown(null, null);

        long ticket = createTicket();
        createPr(ticket, "test-org/pr-insights-req", 931, Instant.now().minus(Duration.ofHours(1)), "wow");
        escalateTicket(ticket, "wow", "bot");

        // a bot SLA-breach escalation is automated, not a human intervention
        var after = getRequestBreakdown(null, null);
        assertThat(after.totalPrTickets()).isEqualTo(before.totalPrTickets() + 1);
        assertThat(after.interventionPrTickets()).isEqualTo(before.interventionPrTickets());
    }

    @Test
    public void requestBreakdown_prTicketWithBotAndManualEscalationsCountsOnceAsIntervention() {
        var before = getRequestBreakdown(null, null);

        // The escalation_open_unique index allows only one open escalation per (ticket, team), so
        // to give one PR ticket BOTH a bot and a manual escalation they must go to different teams
        // (bot auto-escalated to one team, a human manually escalated to another). The presence of
        // the manual row qualifies it as intervention, and COUNT(DISTINCT) counts the ticket once.
        long ticket = createTicket();
        createPr(ticket, "test-org/pr-insights-req", 932, Instant.now().minus(Duration.ofHours(1)), "wow");
        escalateTicket(ticket, "wow", "bot");
        escalateTicket(ticket, "platform", "manual");

        var after = getRequestBreakdown(null, null);
        assertThat(after.totalPrTickets()).isEqualTo(before.totalPrTickets() + 1);
        assertThat(after.interventionPrTickets()).isEqualTo(before.interventionPrTickets() + 1);
    }

    @Test
    public void requestBreakdown_manualEscalationOnNonPrTicketDoesNotCountAsIntervention() {
        var before = getRequestBreakdown(null, null);

        long ticket = createTicket(); // no PR
        escalateTicket(ticket, "wow", "manual");

        // intervention requires a PR ticket; a manual escalation on a plain support ticket is
        // outside the bot-handled funnel entirely
        var after = getRequestBreakdown(null, null);
        assertThat(after.totalSupportTickets()).isEqualTo(before.totalSupportTickets() + 1);
        assertThat(after.totalPrTickets()).isEqualTo(before.totalPrTickets());
        assertThat(after.interventionPrTickets()).isEqualTo(before.interventionPrTickets());
    }

    @Test
    public void requestBreakdown_realisticMixIsCountedCoherently() {
        var before = getRequestBreakdown(null, null);
        Instant prCreatedAt = Instant.now().minus(Duration.ofHours(1));

        long prManual = createTicket();
        long prBotOnly = createTicket();
        long prClean = createTicket();
        createTicket(); // plain, no PR

        createPr(prManual, "test-org/pr-insights-req", 950, prCreatedAt, "wow");
        createPr(prBotOnly, "test-org/pr-insights-req", 951, prCreatedAt, "wow");
        createPr(prClean, "test-org/pr-insights-req", 952, prCreatedAt, "wow");
        escalateTicket(prManual, "wow", "manual");
        escalateTicket(prBotOnly, "wow", "bot");

        // 4 tickets in, 3 are PR tickets, 1 of those needed a manual escalation
        var after = getRequestBreakdown(null, null);
        assertThat(after.totalSupportTickets()).isEqualTo(before.totalSupportTickets() + 4);
        assertThat(after.totalPrTickets()).isEqualTo(before.totalPrTickets() + 3);
        assertThat(after.interventionPrTickets()).isEqualTo(before.interventionPrTickets() + 1);
    }

    @Test
    public void requestBreakdown_anchorsOnTicketCreationDateNotPrCreatedAt() {
        // a ticket created now whose PR was opened 200 days ago
        long ticket = createTicket();
        createPr(ticket, "test-org/pr-insights-req", 940, Instant.now().minus(Duration.ofDays(200)), "wow");

        // recent window: the ticket (created now) is in range, so it counts as a PR ticket even
        // though the PR is 200 days old — proving the anchor is query.date, not pr_created_at
        var recent = getRequestBreakdown(
                LocalDate.now().minusDays(7), LocalDate.now().plusDays(1));
        assertThat(recent.totalPrTickets()).isEqualTo(1);
        assertThat(recent.totalSupportTickets()).isGreaterThanOrEqualTo(1);

        // window around the PR's creation date but before the ticket existed: nothing matches,
        // because the ticket was not created then. A pr_created_at anchor would wrongly count it.
        var aroundPrDate = getRequestBreakdown(
                LocalDate.now().minusDays(250), LocalDate.now().minusDays(150));
        assertThat(aroundPrDate.totalPrTickets()).isEqualTo(0);
        assertThat(aroundPrDate.interventionPrTickets()).isEqualTo(0);
    }

    @Test
    public void requestBreakdown_filtersByOpenEndedDateRanges() {
        // a ticket + PR created now; exercises the single-bound SQL branches (dateFrom-only and
        // dateTo-only), which build the WHERE clause and bind list independently of the two-bound
        // case — a misaligned bind would only surface here. totalPrTickets is isolated per test
        // (pr_tracking is wiped in @BeforeEach), so exact 1/0 assertions are safe.
        long ticket = createTicket();
        createPr(ticket, "test-org/pr-insights-req", 955, Instant.now().minus(Duration.ofHours(1)), "wow");
        LocalDate today = LocalDate.now();

        // dateFrom-only: from yesterday includes today's ticket; from tomorrow excludes it
        assertThat(getRequestBreakdown(today.minusDays(1), null).totalPrTickets())
                .isEqualTo(1);
        assertThat(getRequestBreakdown(today.plusDays(1), null).totalPrTickets())
                .isEqualTo(0);

        // dateTo-only: up to tomorrow includes today's ticket; up to yesterday excludes it
        assertThat(getRequestBreakdown(null, today.plusDays(1)).totalPrTickets())
                .isEqualTo(1);
        assertThat(getRequestBreakdown(null, today.minusDays(1)).totalPrTickets())
                .isEqualTo(0);
    }

    @Test
    public void requestBreakdown_returnsZerosWhenNoDataInRange() {
        // given — a ticket with a PR created now
        long ticketId = createTicket();
        createPr(ticketId, "test-org/pr-insights-req", 910, Instant.now().minus(Duration.ofHours(1)), "wow");

        // when — querying a past date range that predates any ticket
        var response = getRequestBreakdown(LocalDate.of(2000, 1, 1), LocalDate.of(2000, 12, 31));

        // then — nothing matches
        assertThat(response.totalSupportTickets()).isEqualTo(0);
        assertThat(response.totalPrTickets()).isEqualTo(0);
        assertThat(response.interventionPrTickets()).isEqualTo(0);
    }

    private SupportBotClient.RequestBreakdownResponse getRequestBreakdown(
            @Nullable LocalDate dateFrom, @Nullable LocalDate dateTo) {
        return supportBotClient.tenantInsights().requestBreakdown(dateFrom, dateTo);
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
}
