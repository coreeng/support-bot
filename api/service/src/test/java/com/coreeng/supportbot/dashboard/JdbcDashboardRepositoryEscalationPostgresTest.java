package com.coreeng.supportbot.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;

import com.coreeng.supportbot.dashboard.DashboardData.EscalationRate;
import com.coreeng.supportbot.dashboard.DashboardData.EscalationRateGranularity;
import com.coreeng.supportbot.dashboard.DashboardData.EscalationRatePoint;
import com.coreeng.supportbot.dashboard.DashboardData.EscalationTopic;
import com.coreeng.supportbot.dashboard.DashboardData.ResolutionByEscalation;
import com.coreeng.supportbot.dashboard.DashboardData.TeamScorecard;
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

/**
 * Runs the escalation dashboard SQL against Postgres. Fixtures sit in a fixed week in January 2001, so the date window
 * isolates them from any other rows in the database.
 */
@SpringJUnitConfig(JdbcDashboardRepositoryEscalationPostgresTest.TestConfig.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@EnabledIf("localDatabaseEnabled")
class JdbcDashboardRepositoryEscalationPostgresTest {
    private static final String CHANNEL = "C_DASH_ESC_TEST";
    private static final String TAG_X = "dash-esc-test-x";
    private static final String TAG_Y = "dash-esc-test-y";
    private static final String TEAM_A = "dash-esc-team-a";
    private static final String TEAM_B = "dash-esc-team-b";
    private static final LocalDate FROM = LocalDate.of(2001, 1, 8); // Monday
    private static final LocalDate TO = LocalDate.of(2001, 1, 12); // Friday
    private static final double HOUR = 3600.0;

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

        // Monday: one escalated ticket (4h to close, 2h escalation) and one plain ticket (1h to close).
        long t1 = insertTicket(at(8, 9), at(8, 13));
        insertEscalation(t1, TEAM_A, at(8, 10), at(8, 12), TAG_X);
        insertTicket(at(8, 9), at(8, 10));

        // Tuesday: two escalated tickets; one closed (8h, 6h escalation), one still open with an open escalation.
        long t3 = insertTicket(at(9, 9), at(9, 17));
        insertEscalation(t3, TEAM_A, at(9, 10), at(9, 16), TAG_X, TAG_Y);
        long t4 = insertTicket(at(9, 9), null);
        insertEscalation(t4, TEAM_B, at(9, 11), null, TAG_Y);

        // Wednesday: one open ticket, never escalated.
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
        jdbcTemplate.update("DELETE FROM tag WHERE code IN (?, ?)", TAG_X, TAG_Y);
    }

    @Test
    void escalationRateIsShareOfEachDaysTicketCohort() {
        EscalationRate rate = repository.getEscalationRate(FROM, TO);

        assertThat(rate.granularity()).isEqualTo(EscalationRateGranularity.DAY);
        assertThat(rate.data())
                .extracting(
                        EscalationRatePoint::date, EscalationRatePoint::tickets, EscalationRatePoint::escalatedTickets)
                .containsExactly(tuple("2001-01-08", 2L, 1L), tuple("2001-01-09", 2L, 2L), tuple("2001-01-10", 1L, 0L));
        assertThat(rate.data()).extracting(EscalationRatePoint::rate).containsExactly(0.5, 1.0, 0.0);
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
    void escalatedResolutionRunsToTheLastEscalationResolutionWhenTheCloseIsLoggedEarlier() {
        // A ticket's close is logged at its last human thread message. With no replies after the ticket form,
        // that is a moment after opening, even though the escalation (and the real close) came hours later.
        LocalDateTime opened = LocalDateTime.of(2001, 1, 15, 9, 0);
        long ticket = insertTicket(opened, opened.plusNanos(400_000_000));
        insertEscalation(ticket, TEAM_A, LocalDateTime.of(2001, 1, 15, 10, 0), LocalDateTime.of(2001, 1, 15, 13, 0));

        LocalDate monday = LocalDate.of(2001, 1, 15);
        List<ResolutionByEscalation> result = repository.getResolutionEscalatedVsNot(monday, monday);

        assertThat(result).singleElement().satisfies(r -> {
            assertThat(r.escalated()).isTrue();
            assertThat(r.p50()).isCloseTo(4 * HOUR, within(1.0));
        });
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
    void topicsAreRankedByTotalBusinessTimeOfResolvedEscalations() {
        List<EscalationTopic> result = repository.getEscalationTopics(FROM, TO);

        assertThat(result).extracting(EscalationTopic::tag).containsExactly(TAG_X, TAG_Y);
        EscalationTopic x = result.get(0);
        assertThat(x.escalations()).isEqualTo(2);
        assertThat(x.totalSeconds()).isCloseTo(8 * HOUR, within(1.0));
        assertThat(x.avgSeconds()).isCloseTo(4 * HOUR, within(1.0));

        EscalationTopic y = result.get(1);
        assertThat(y.escalations()).isEqualTo(1); // the open escalation is not counted
        assertThat(y.totalSeconds()).isCloseTo(6 * HOUR, within(1.0));
    }

    private static LocalDateTime at(int dayOfJanuary2001, int hour) {
        return LocalDateTime.of(2001, 1, dayOfJanuary2001, hour, 0);
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
            long ticketId, String team, LocalDateTime openedAt, @Nullable LocalDateTime resolvedAt, String... tags) {
        Long id = jdbcTemplate.queryForObject(
                """
                INSERT INTO escalation (ticket_id, channel_id, status, team)
                VALUES (?, ?, ?::escalation_status, ?) RETURNING id
                """, Long.class, ticketId, CHANNEL, resolvedAt != null ? "resolved" : "opened", team);
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
