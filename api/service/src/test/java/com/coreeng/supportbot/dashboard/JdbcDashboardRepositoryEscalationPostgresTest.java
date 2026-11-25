package com.coreeng.supportbot.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;

import com.coreeng.supportbot.dashboard.DashboardData.EscalationRate;
import com.coreeng.supportbot.dashboard.DashboardData.EscalationRatePoint;
import com.coreeng.supportbot.dashboard.DashboardData.EscalationTopic;
import com.coreeng.supportbot.dashboard.DashboardData.Granularity;
import com.coreeng.supportbot.dashboard.DashboardData.ResolutionByEscalation;
import com.coreeng.supportbot.dashboard.DashboardData.TeamEscalationRate;
import com.coreeng.supportbot.dashboard.DashboardData.TeamScorecard;
import com.coreeng.supportbot.dashboard.DashboardData.TimeBeforeEscalation;
import com.coreeng.supportbot.summary.SummaryTestFixtures;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/** Escalation dashboard SQL against Postgres; fixtures sit in a fixed week in January 2001. */
@SpringJUnitConfig(JdbcDashboardRepositoryEscalationPostgresTest.TestConfig.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@EnabledIf("localDatabaseEnabled")
class JdbcDashboardRepositoryEscalationPostgresTest {
    private static final String CHANNEL = "C_DASH_ESC_TEST";
    private static final String TAG_X = "dash-esc-test-x";
    private static final String TAG_Y = "dash-esc-test-y";
    private static final String TAG_SHARED_1 = "dash-esc-test-shared-1";
    private static final String TAG_SHARED_2 = "dash-esc-test-shared-2";
    private static final String SHARED_LABEL = "dash-esc-test-shared";
    private static final String TEAM_A = "dash-esc-team-a";
    private static final String TEAM_B = "dash-esc-team-b";
    private static final String REQUESTING_A = "dash-esc-requesting-a";
    private static final String REQUESTING_B = "dash-esc-requesting-b";
    private static final String REQUESTING_C = "dash-esc-requesting-c";
    private static final LocalDate FROM = LocalDate.of(2001, 1, 8);
    private static final LocalDate TO = LocalDate.of(2001, 1, 12);
    private static final double HOUR = 3600.0;
    private static final double MINUTE = 60.0;

    private final JdbcDashboardRepository repository;
    private final JdbcTemplate jdbcTemplate;
    private final List<Long> ticketIds = new ArrayList<>();
    private final List<Long> escalationIds = new ArrayList<>();

    JdbcDashboardRepositoryEscalationPostgresTest(JdbcDashboardRepository repository, JdbcTemplate jdbcTemplate) {
        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
    }

    static boolean localDatabaseEnabled() {
        return Boolean.getBoolean("docker") || "true".equals(System.getenv("SUPPORTBOT_USE_LOCAL_DB"));
    }

    @BeforeEach
    void seed() {
        jdbcTemplate.update(
                "INSERT INTO tag (code, label) VALUES (?, ?), (?, ?) ON CONFLICT (code) DO NOTHING",
                TAG_X,
                TAG_X,
                TAG_Y,
                TAG_Y);

        // Day 1: one escalated ticket (4h to close, 2h escalation) and one plain ticket (1h to close).
        long t1 = insertTicket(at(8, 9), at(8, 13));
        insertEscalation(t1, TEAM_A, at(8, 10), at(8, 12), TAG_X);
        insertTicket(at(8, 9), at(8, 10));

        // Day 2: two escalated tickets; one closed (8h, 6h escalation), one still open with an open escalation.
        long t3 = insertTicket(at(9, 9), at(9, 17));
        insertEscalation(t3, TEAM_A, at(9, 10), at(9, 16), TAG_X, TAG_Y);
        long t4 = insertTicket(at(9, 9), null);
        insertEscalation(t4, TEAM_B, at(9, 11), null, TAG_Y);

        // Day 3: one open ticket, never escalated.
        insertTicket(at(10, 9), null);
    }

    @AfterEach
    void cleanUp() {
        escalationIds.forEach(id -> {
            jdbcTemplate.update("DELETE FROM escalation_to_tag WHERE escalation_id = ?", id);
            jdbcTemplate.update("DELETE FROM escalation_log WHERE escalation_id = ?", id);
            jdbcTemplate.update("DELETE FROM escalation WHERE id = ?", id);
        });
        ticketIds.forEach(id -> {
            Long queryId = jdbcTemplate.queryForObject("SELECT query_id FROM ticket WHERE id = ?", Long.class, id);
            jdbcTemplate.update("DELETE FROM ticket_log WHERE ticket_id = ?", id);
            jdbcTemplate.update("DELETE FROM ticket WHERE id = ?", id);
            jdbcTemplate.update("DELETE FROM query WHERE id = ?", queryId);
        });
        jdbcTemplate.update("DELETE FROM tag WHERE code IN (?, ?, ?, ?)", TAG_X, TAG_Y, TAG_SHARED_1, TAG_SHARED_2);
    }

    @Test
    void escalationRateIsShareOfEachDaysTicketCohort() {
        EscalationRate rate = repository.getEscalationRate(FROM, TO);

        // Days 4 and 5 have no tickets, so they are filled with zeros.
        assertThat(rate.granularity()).isEqualTo(Granularity.DAY);
        assertThat(rate.data())
                .extracting(
                        EscalationRatePoint::date, EscalationRatePoint::tickets, EscalationRatePoint::escalatedTickets)
                .containsExactly(
                        tuple("2001-01-08", 2L, 1L),
                        tuple("2001-01-09", 2L, 2L),
                        tuple("2001-01-10", 1L, 0L),
                        tuple("2001-01-11", 0L, 0L),
                        tuple("2001-01-12", 0L, 0L));
        assertThat(rate.data()).extracting(EscalationRatePoint::rate).containsExactly(0.5, 1.0, 0.0, 0.0, 0.0);
    }

    @Test
    void weeklyEscalationRateFillsEmptyWeeksAndLabelsTheFirstFromTheWindowStart() {
        // Starts on day 3, part-way through a week: days 1 and 2 fall outside, so the first week holds day 3 only.
        LocalDate from = LocalDate.of(2001, 1, 10);
        LocalDate to = LocalDate.of(2001, 2, 20);

        EscalationRate rate = repository.getEscalationRate(from, to);

        assertThat(rate.granularity()).isEqualTo(Granularity.WEEK);
        assertThat(rate.data())
                .extracting(
                        EscalationRatePoint::date, EscalationRatePoint::tickets, EscalationRatePoint::escalatedTickets)
                .containsExactly(
                        tuple("2001-01-10", 1L, 0L),
                        tuple("2001-01-15", 0L, 0L),
                        tuple("2001-01-22", 0L, 0L),
                        tuple("2001-01-29", 0L, 0L),
                        tuple("2001-02-05", 0L, 0L),
                        tuple("2001-02-12", 0L, 0L),
                        tuple("2001-02-19", 0L, 0L));
    }

    @Test
    void resolutionIsSplitByWhetherTheTicketWasEscalated() {
        List<ResolutionByEscalation> result = repository.getResolutionEscalatedVsNot(FROM, TO);

        assertThat(result).hasSize(2);
        ResolutionByEscalation escalated = result.get(0);
        assertThat(escalated.escalated()).isTrue();
        assertThat(escalated.tickets()).isEqualTo(2);
        assertThat(escalated.p50()).isCloseTo(6 * HOUR, within(1.0));
        assertThat(escalated.p90()).isCloseTo(7.6 * HOUR, within(1.0));

        ResolutionByEscalation notEscalated = result.get(1);
        assertThat(notEscalated.escalated()).isFalse();
        assertThat(notEscalated.tickets()).isEqualTo(1);
        assertThat(notEscalated.p50()).isCloseTo(HOUR, within(1.0));
    }

    @Test
    void resolutionEndsAtTheLastInteractionEvenWhenTheEscalationIsClosedLater() {
        // Opened 10:00, escalated 10:30, last message 11:16 (the logged close); close clicked at 12:00, which also
        // auto-resolved the escalation.
        LocalDate day = LocalDate.of(2001, 1, 15);
        long ticket = insertTicket(at(15, 10, 0), at(15, 11, 16));
        insertEscalation(ticket, TEAM_A, at(15, 10, 30), at(15, 12, 0), TAG_X);

        assertThat(repository.getResolutionEscalatedVsNot(day, day))
                .singleElement()
                .satisfies(r -> {
                    assertThat(r.escalated()).isTrue();
                    assertThat(r.p50()).isCloseTo(76 * MINUTE, within(1.0));
                });
        assertThat(repository.getEscalationTeamScorecard(day, day))
                .singleElement()
                .satisfies(r -> assertThat(r.resolutionP50()).isCloseTo(46 * MINUTE, within(1.0)));
        assertThat(repository.getEscalationTopics(day, day)).singleElement().satisfies(r -> assertThat(r.totalSeconds())
                .isCloseTo(46 * MINUTE, within(1.0)));
    }

    @Test
    void escalationOnAReopenedTicketEndsAtTheFirstCloseAfterItOpened() {
        // Opened 10:00, escalated 10:30, last message 11:16; close clicked at 12:00 resolved the escalation.
        // Reopened 13:00 and closed again 13:30.
        LocalDate day = LocalDate.of(2001, 1, 16);
        long ticket = insertTicket(at(16, 10, 0), at(16, 11, 16));
        insertEscalation(ticket, TEAM_A, at(16, 10, 30), at(16, 12, 0), TAG_X);
        addTicketLog(ticket, "opened", at(16, 13, 0));
        addTicketLog(ticket, "closed", at(16, 13, 30));

        assertThat(repository.getEscalationTeamScorecard(day, day))
                .singleElement()
                .satisfies(r -> assertThat(r.resolutionP50()).isCloseTo(46 * MINUTE, within(1.0)));
        assertThat(repository.getEscalationTopics(day, day)).singleElement().satisfies(r -> assertThat(r.totalSeconds())
                .isCloseTo(46 * MINUTE, within(1.0)));
    }

    @Test
    void escalationWithNoMessageAfterItEndsAtZero() {
        // Closed (last message 09:10) and reopened 09:30 before this escalation. Last message 10:20, escalated
        // 10:30, nobody writes again; close clicked at 12:00 is logged at 10:20 and resolves the escalation.
        LocalDate day = LocalDate.of(2001, 1, 18);
        long ticket = insertTicket(at(18, 9, 0), at(18, 9, 10));
        addTicketLog(ticket, "opened", at(18, 9, 30));
        addTicketLog(ticket, "closed", at(18, 10, 20));
        insertEscalation(ticket, TEAM_A, at(18, 10, 30), at(18, 12, 0), TAG_X);

        assertThat(repository.getEscalationTeamScorecard(day, day))
                .singleElement()
                .satisfies(r -> assertThat(r.resolutionP50()).isZero());
        assertThat(repository.getEscalationTopics(day, day)).singleElement().satisfies(r -> {
            assertThat(r.escalations()).isEqualTo(1);
            assertThat(r.totalSeconds()).isZero();
        });
    }

    @Test
    void escalationIgnoresClosesFromBeforeTheTicketWasReopened() {
        // Closed 09:10 and reopened 09:30; escalated 10:30, last message 11:00 (the logged close).
        LocalDate day = LocalDate.of(2001, 1, 19);
        long ticket = insertTicket(at(19, 9, 0), at(19, 9, 10));
        addTicketLog(ticket, "opened", at(19, 9, 30));
        addTicketLog(ticket, "closed", at(19, 11, 0));
        insertEscalation(ticket, TEAM_A, at(19, 10, 30), at(19, 12, 0), TAG_X);

        assertThat(repository.getEscalationTeamScorecard(day, day))
                .singleElement()
                .satisfies(r -> assertThat(r.resolutionP50()).isCloseTo(30 * MINUTE, within(1.0)));
    }

    @Test
    void escalationAfterAReopenWithNoMessageEndsAtZero() {
        // A reopen (or a message reviving a stale ticket) sets the last interaction a moment before it logs the
        // open; a later close with no message in between is logged at that earlier moment.
        LocalDate day = LocalDate.of(2001, 1, 22);
        LocalDateTime lastInteraction = at(22, 9, 30);
        LocalDateTime reopenLogged = lastInteraction.plusNanos(50_000_000);

        long reopened = insertTicket(at(22, 9, 0), at(22, 9, 10));
        addTicketLog(reopened, "opened", reopenLogged);
        addTicketLog(reopened, "closed", lastInteraction);
        insertEscalation(reopened, TEAM_A, at(22, 10, 0), at(22, 11, 0), TAG_X);

        long revived = insertTicket(at(22, 9, 0), null);
        addTicketLog(revived, "stale", at(22, 9, 10));
        addTicketLog(revived, "opened", reopenLogged);
        addTicketLog(revived, "closed", lastInteraction);
        insertEscalation(revived, TEAM_B, at(22, 10, 0), at(22, 11, 0), TAG_Y);

        assertThat(repository.getEscalationTeamScorecard(day, day))
                .extracting(TeamScorecard::team, TeamScorecard::resolutionP50)
                .containsExactly(tuple(TEAM_A, 0.0), tuple(TEAM_B, 0.0));
    }

    @Test
    void topicsKeepTagsThatShareALabelApart() {
        jdbcTemplate.update(
                "INSERT INTO tag (code, label) VALUES (?, ?), (?, ?) ON CONFLICT (code) DO NOTHING",
                TAG_SHARED_1,
                SHARED_LABEL,
                TAG_SHARED_2,
                SHARED_LABEL);
        LocalDate day = LocalDate.of(2001, 1, 23);
        long ticket = insertTicket(at(23, 9), at(23, 12));
        insertEscalation(ticket, TEAM_A, at(23, 10), at(23, 11), TAG_SHARED_1, TAG_SHARED_2);
        // A tag with only an open escalation has nothing timed, so it is left out.
        long open = insertTicket(at(23, 9), null);
        insertEscalation(open, TEAM_A, at(23, 10), null, TAG_Y);

        List<EscalationTopic> result = repository.getEscalationTopics(day, day);

        assertThat(result).extracting(EscalationTopic::tag).containsExactly(TAG_SHARED_1, TAG_SHARED_2);
        assertThat(result).allSatisfy(r -> {
            assertThat(r.escalations()).isEqualTo(1);
            assertThat(r.totalSeconds()).isCloseTo(HOUR, within(1.0));
            assertThat(r.avgWaitSeconds()).isCloseTo(HOUR, within(1.0));
        });
    }

    @Test
    void timeBeforeEscalationSkipsAnEscalationLoggedBeforeTheTicketOpened() {
        LocalDate day = LocalDate.of(2001, 1, 24);
        long ticket = insertTicket(at(24, 9), null);
        insertEscalation(ticket, TEAM_A, at(24, 8), null, TAG_X);
        insertEscalation(ticket, TEAM_B, at(24, 10), null, TAG_X);

        TimeBeforeEscalation result = repository.getTimeBeforeEscalation(day, day);

        assertThat(result.escalations()).isEqualTo(1);
        assertThat(result.p50()).isCloseTo(HOUR, within(1.0));
    }

    @Test
    void scorecardLabelsEscalationsWithNoTeamAsUnassigned() {
        LocalDate day = LocalDate.of(2001, 1, 25);
        long ticket = insertTicket(at(25, 9), null);
        insertEscalation(ticket, null, at(25, 10), null, TAG_X);

        assertThat(repository.getEscalationTeamScorecard(day, day))
                .extracting(TeamScorecard::team)
                .containsExactly("Unassigned");
    }

    @Test
    void resolutionLeavesOutTicketsThatAreOpenAgain() {
        LocalDate day = LocalDate.of(2001, 1, 16);
        long ticket = insertTicket(at(16, 10), at(16, 11));
        addTicketLog(ticket, "opened", at(16, 13));
        jdbcTemplate.update("UPDATE ticket SET status = 'opened'::ticket_status WHERE id = ?", ticket);

        assertThat(repository.getResolutionEscalatedVsNot(day, day)).isEmpty();
    }

    @Test
    void zeroDurationsAreKept() {
        // Everything happens outside counted time, so every duration is zero.
        LocalDate day = LocalDate.of(2001, 1, 13);
        long ticket = insertTicket(at(13, 10), at(13, 11));
        insertEscalation(ticket, TEAM_A, at(13, 10, 15), at(13, 10, 45), TAG_X);

        assertThat(repository.getResolutionEscalatedVsNot(day, day))
                .singleElement()
                .satisfies(r -> {
                    assertThat(r.tickets()).isEqualTo(1);
                    assertThat(r.p50()).isZero();
                });
        assertThat(repository.getEscalationTeamScorecard(day, day))
                .singleElement()
                .satisfies(r -> assertThat(r.resolutionP50()).isZero());
        assertThat(repository.getEscalationTopics(day, day)).singleElement().satisfies(r -> {
            assertThat(r.escalations()).isEqualTo(1);
            assertThat(r.totalSeconds()).isZero();
        });
        assertThat(repository.getTimeBeforeEscalation(day, day).escalations()).isEqualTo(1);
    }

    @Test
    void escalationRateCountsATicketOnceWhateverItsEscalations() {
        // One ticket escalated manually and by the bot, one escalated by the bot only, one never escalated.
        LocalDate day = LocalDate.of(2001, 1, 17);
        long both = insertTicket(at(17, 9), null);
        insertEscalation(both, TEAM_A, at(17, 10), null, TAG_X);
        insertEscalationWithSource(both, TEAM_B, at(17, 11), null, "bot");
        long botOnly = insertTicket(at(17, 9), null);
        insertEscalationWithSource(botOnly, TEAM_A, at(17, 10), null, "bot");
        insertTicket(at(17, 9), null);

        assertThat(repository.getEscalationRate(day, day).data())
                .extracting(EscalationRatePoint::tickets, EscalationRatePoint::escalatedTickets)
                .containsExactly(tuple(3L, 2L));
        assertThat(repository.getEscalationRateByTeam(day, day))
                .extracting(TeamEscalationRate::team, TeamEscalationRate::tickets, TeamEscalationRate::escalatedTickets)
                .containsExactly(tuple("Unassigned", 3L, 2L));
    }

    @Test
    void scorecardCountsOpenEscalationsButOnlyTimesResolvedOnes() {
        List<TeamScorecard> result = repository.getEscalationTeamScorecard(FROM, TO);

        assertThat(result).extracting(TeamScorecard::team).containsExactly(TEAM_A, TEAM_B);
        TeamScorecard teamA = result.get(0);
        assertThat(teamA.escalations()).isEqualTo(2);
        assertThat(teamA.open()).isZero();
        assertThat(teamA.resolutionP50()).isCloseTo(4 * HOUR, within(1.0));
        assertThat(teamA.resolutionP90()).isCloseTo(5.6 * HOUR, within(1.0));

        TeamScorecard teamB = result.get(1);
        assertThat(teamB.escalations()).isEqualTo(1);
        assertThat(teamB.open()).isEqualTo(1);
        assertThat(teamB.resolutionP50()).isNull();
        assertThat(teamB.resolutionP90()).isNull();
    }

    @Test
    void topicsAreRankedByTotalTimeOfResolvedEscalations() {
        // A resolved bot escalation: it counts towards time in escalation, but not towards the wait before it.
        long botTicket = insertTicket(at(11, 9), at(11, 15));
        insertEscalationWithSource(botTicket, TEAM_A, at(11, 13), at(11, 14), "bot", TAG_Y);

        List<EscalationTopic> result = repository.getEscalationTopics(FROM, TO);

        assertThat(result).extracting(EscalationTopic::tag).containsExactly(TAG_X, TAG_Y);
        EscalationTopic x = result.get(0);
        assertThat(x.escalations()).isEqualTo(2);
        assertThat(x.totalSeconds()).isCloseTo(8 * HOUR, within(1.0));
        assertThat(x.avgSeconds()).isCloseTo(4 * HOUR, within(1.0));
        assertThat(x.avgWaitSeconds()).isCloseTo(HOUR, within(1.0));

        // Day 2's resolved manual escalation (6h) and the bot one (1h) are timed; the open one is left out.
        // So the wait covers only the resolved manual one (1h), not the open one (2h).
        EscalationTopic y = result.get(1);
        assertThat(y.escalations()).isEqualTo(2);
        assertThat(y.totalSeconds()).isCloseTo(7 * HOUR, within(1.0));
        assertThat(y.avgWaitSeconds()).isCloseTo(HOUR, within(1.0));
    }

    @Test
    void timeBeforeEscalationMeasuresEachTicketsFirstManualEscalation() {
        // A second escalation on an already-escalated ticket, and a bot escalation, must not add waits.
        insertEscalationWithSource(ticketIds.get(0), TEAM_B, at(8, 16), null, "manual", TAG_X);
        long botTicket = insertTicket(at(11, 9), null);
        insertEscalationWithSource(botTicket, TEAM_A, at(11, 15), null, "bot", TAG_X);

        // The window follows ticket opening: a ticket opened before it is left out even if escalated inside it.
        long openedBefore = insertTicket(LocalDateTime.of(2001, 1, 5, 9, 0), null);
        insertEscalation(openedBefore, TEAM_A, at(8, 10), null, TAG_X);

        TimeBeforeEscalation result = repository.getTimeBeforeEscalation(FROM, TO);

        // Waits: day 1 1h, day 2 1h and 2h.
        assertThat(result.escalations()).isEqualTo(3);
        assertThat(result.p50()).isCloseTo(HOUR, within(1.0));
        assertThat(result.p90()).isCloseTo(1.8 * HOUR, within(1.0));
    }

    @Test
    void escalationRateByTeamGroupsTheTicketCohortByRequestingTeam() {
        // Seed order: day 1 escalated, day 1 plain, day 2 escalated (closed), day 2 escalated (open), day 3.
        setTicketTeam(ticketIds.get(0), REQUESTING_A);
        setTicketTeam(ticketIds.get(1), REQUESTING_A);
        setTicketTeam(ticketIds.get(2), REQUESTING_B);
        setTicketTeam(ticketIds.get(3), REQUESTING_B);
        setTicketTeam(ticketIds.get(4), REQUESTING_C);

        List<TeamEscalationRate> result = repository.getEscalationRateByTeam(FROM, TO);

        // The team with no escalations is left out.
        assertThat(result)
                .extracting(
                        TeamEscalationRate::team,
                        TeamEscalationRate::tickets,
                        TeamEscalationRate::escalatedTickets,
                        TeamEscalationRate::rate)
                .containsExactly(tuple(REQUESTING_B, 2L, 2L, 1.0), tuple(REQUESTING_A, 2L, 1L, 0.5));
    }

    private void addTicketLog(long ticketId, String event, LocalDateTime date) {
        jdbcTemplate.update(
                "INSERT INTO ticket_log (ticket_id, event, date) VALUES (?, ?::ticket_event_type, ?)",
                ticketId,
                event,
                utc(date));
    }

    private void setTicketTeam(long ticketId, String team) {
        jdbcTemplate.update("UPDATE ticket SET team = ? WHERE id = ?", team, ticketId);
    }

    private static LocalDateTime at(int dayOfJanuary2001, int hour) {
        return at(dayOfJanuary2001, hour, 0);
    }

    private static LocalDateTime at(int dayOfJanuary2001, int hour, int minute) {
        return LocalDateTime.of(2001, 1, dayOfJanuary2001, hour, minute);
    }

    private static Timestamp utc(LocalDateTime time) {
        return Timestamp.from(time.toInstant(ZoneOffset.UTC));
    }

    private long insertTicket(LocalDateTime openedAt, @Nullable LocalDateTime closedAt) {
        long id = SummaryTestFixtures.insertTicket(
                jdbcTemplate,
                CHANNEL,
                "978912000." + UUID.randomUUID().toString().substring(0, 6),
                openedAt,
                closedAt != null ? "closed" : "opened",
                null);
        ticketIds.add(id);
        jdbcTemplate.update(
                "INSERT INTO ticket_log (ticket_id, event, date) VALUES (?, 'opened'::ticket_event_type, ?)",
                id,
                utc(openedAt));
        if (closedAt != null) {
            jdbcTemplate.update(
                    "INSERT INTO ticket_log (ticket_id, event, date) VALUES (?, 'closed'::ticket_event_type, ?)",
                    id,
                    utc(closedAt));
        }
        return id;
    }

    private void insertEscalation(
            long ticketId,
            @Nullable String team,
            LocalDateTime openedAt,
            @Nullable LocalDateTime resolvedAt,
            String... tags) {
        insertEscalationWithSource(ticketId, team, openedAt, resolvedAt, "manual", tags);
    }

    private void insertEscalationWithSource(
            long ticketId,
            @Nullable String team,
            LocalDateTime openedAt,
            @Nullable LocalDateTime resolvedAt,
            String source,
            String... tags) {
        Long id = jdbcTemplate.queryForObject(
                """
                INSERT INTO escalation (ticket_id, channel_id, status, team, source)
                VALUES (?, ?, ?::escalation_status, ?, ?) RETURNING id
                """, Long.class, ticketId, CHANNEL, resolvedAt != null ? "resolved" : "opened", team, source);
        if (id == null) {
            throw new IllegalStateException("escalation insert returned no id");
        }
        escalationIds.add(id);
        jdbcTemplate.update(
                "INSERT INTO escalation_log (escalation_id, event, date) VALUES (?, 'opened', ?)", id, utc(openedAt));
        if (resolvedAt != null) {
            jdbcTemplate.update(
                    "INSERT INTO escalation_log (escalation_id, event, date) VALUES (?, 'resolved', ?)",
                    id,
                    utc(resolvedAt));
        }
        for (String tag : tags) {
            jdbcTemplate.update("INSERT INTO escalation_to_tag (escalation_id, tag_code) VALUES (?, ?)", id, tag);
        }
    }

    @Configuration
    static class TestConfig {
        @Bean
        DataSource dataSource() {
            String url = System.getProperty("supportbot.localDb.url", "jdbc:postgresql://localhost:5432/postgres");
            String username = System.getProperty("supportbot.localDb.user", "postgres");
            String password = System.getProperty("supportbot.localDb.password", "postgres");
            return new DriverManagerDataSource(url, username, password);
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        DSLContext dslContext(DataSource dataSource) {
            return DSL.using(new TransactionAwareDataSourceProxy(dataSource), SQLDialect.POSTGRES);
        }

        @Bean
        JdbcDashboardRepository jdbcDashboardRepository(DSLContext dslContext) {
            return new JdbcDashboardRepository(dslContext);
        }
    }
}
