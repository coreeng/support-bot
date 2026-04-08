package com.coreeng.supportbot.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class JdbcDashboardRepositoryStartResolutionTest {

    @Test
    void usesExplicitDateFromWhenNotAllTime() {
        LocalDate dateFrom = LocalDate.of(2026, 3, 1);

        LocalDate start = JdbcDashboardRepository.resolveIncomingVsResolvedStart(
                dateFrom, false, LocalDate.of(2026, 3, 10), LocalDate.of(2026, 1, 1));

        assertThat(start).isEqualTo(dateFrom);
    }

    @Test
    void defaultsToSevenDayLookbackWhenNotAllTimeAndDateFromMissing() {
        LocalDate today = LocalDate.of(2026, 3, 10);

        LocalDate start = JdbcDashboardRepository.resolveIncomingVsResolvedStart(null, false, today, null);

        assertThat(start).isEqualTo(today.minusDays(7));
    }

    @Test
    void usesExplicitDateFromWhenAllTimeEvenIfEarliestActivityExists() {
        LocalDate dateFrom = LocalDate.of(2026, 2, 1);

        LocalDate start = JdbcDashboardRepository.resolveIncomingVsResolvedStart(
                dateFrom, true, LocalDate.of(2026, 3, 10), LocalDate.of(2026, 1, 1));

        assertThat(start).isEqualTo(dateFrom);
    }

    @Test
    void fallsBackToEarliestActivityWhenAllTimeAndDateFromMissing() {
        LocalDate earliestActivity = LocalDate.of(2026, 1, 5);

        LocalDate start = JdbcDashboardRepository.resolveIncomingVsResolvedStart(
                null, true, LocalDate.of(2026, 3, 10), earliestActivity);

        assertThat(start).isEqualTo(earliestActivity);
    }

    @Test
    void returnsNullWhenAllTimeAndNoActivityExists() {
        LocalDate start =
                JdbcDashboardRepository.resolveIncomingVsResolvedStart(null, true, LocalDate.of(2026, 3, 10), null);

        assertThat(start).isNull();
    }
}
