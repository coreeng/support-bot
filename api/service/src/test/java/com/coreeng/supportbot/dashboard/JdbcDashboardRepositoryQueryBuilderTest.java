package com.coreeng.supportbot.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class JdbcDashboardRepositoryQueryBuilderTest {

    @Test
    void buildsSeriesQueryWithoutTeamFilters() {
        JdbcDashboardRepository.IncomingVsResolvedSqlQuery query =
                JdbcDashboardRepository.buildIncomingVsResolvedSeriesQuery(
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 1, 5),
                        List.of(),
                        JdbcDashboardRepository.IncomingVsResolvedBucketing.forDateRange(
                                LocalDate.of(2026, 1, 1),
                                LocalDate.of(2026, 1, 5),
                                IncomingVsResolvedQuery.Granularity.AUTO));

        assertThat(query.sql()).contains("COUNT(DISTINCT query_id) AS count");
        assertThat(query.sql()).contains("COUNT(*) AS count");
        assertThat(query.sql()).doesNotContain("team_id IN");
        assertThat(query.bindings())
                .containsExactly("2026-01-01", "2026-01-05", "2026-01-01", "2026-01-05", "2026-01-01", "2026-01-05");
    }

    @Test
    void buildsSeriesQueryWithTeamFiltersInBindingOrder() {
        JdbcDashboardRepository.IncomingVsResolvedSqlQuery query =
                JdbcDashboardRepository.buildIncomingVsResolvedSeriesQuery(
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 3, 15),
                        List.of("team-a", "team-b"),
                        JdbcDashboardRepository.IncomingVsResolvedBucketing.forDateRange(
                                LocalDate.of(2026, 1, 1),
                                LocalDate.of(2026, 3, 15),
                                IncomingVsResolvedQuery.Granularity.DAY));

        assertThat(query.sql()).contains("date_trunc('day', query_posted_ts::timestamptz)");
        assertThat(query.sql()).contains("date_trunc('day', last_closed_ts::timestamptz)");
        assertThat(query.sql()).contains("AND team_id IN (?, ?)");
        assertThat(query.bindings())
                .containsExactly(
                        "2026-01-01",
                        "2026-03-15",
                        "2026-01-01",
                        "2026-03-15",
                        "team-a",
                        "team-b",
                        "2026-01-01",
                        "2026-03-15",
                        "team-a",
                        "team-b");
    }

    @Test
    void buildsEarliestActivityQueryWithoutTeams() {
        JdbcDashboardRepository.IncomingVsResolvedSqlQuery query =
                JdbcDashboardRepository.buildEarliestIncomingVsResolvedActivityQuery(
                        LocalDate.of(2026, 2, 1), List.of());

        assertThat(query.sql()).contains("SELECT MIN(activity_date) AS activity_date");
        assertThat(query.sql()).doesNotContain("team_id IN");
        assertThat(query.bindings()).containsExactly("2026-02-01", "2026-02-01");
    }

    @Test
    void buildsEarliestActivityQueryWithTeamsForBothSides() {
        JdbcDashboardRepository.IncomingVsResolvedSqlQuery query =
                JdbcDashboardRepository.buildEarliestIncomingVsResolvedActivityQuery(
                        LocalDate.of(2026, 2, 1), List.of("team-a", "team-b"));

        assertThat(query.sql()).contains("query_posted_ts::date <= ?::date");
        assertThat(query.sql()).contains("last_closed_ts::date <= ?::date");
        assertThat(query.sql()).contains("AND team_id IN (?, ?)");
        assertThat(query.bindings())
                .containsExactly("2026-02-01", "team-a", "team-b", "2026-02-01", "team-a", "team-b");
    }
}
