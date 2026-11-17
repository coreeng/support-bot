package com.coreeng.supportbot.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.coreeng.supportbot.dashboard.DashboardData.EscalationRateGranularity;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class EscalationRateGranularityTest {

    @Test
    void bucketsByDayUpToAMonth() {
        LocalDate from = LocalDate.of(2026, 1, 1);

        assertThat(JdbcDashboardRepository.resolveEscalationRateGranularity(from, from.plusDays(7)))
                .isEqualTo(EscalationRateGranularity.DAY);
        assertThat(JdbcDashboardRepository.resolveEscalationRateGranularity(from, from.plusDays(31)))
                .isEqualTo(EscalationRateGranularity.DAY);
    }

    @Test
    void bucketsByWeekBeyondAMonth() {
        LocalDate from = LocalDate.of(2026, 1, 1);

        assertThat(JdbcDashboardRepository.resolveEscalationRateGranularity(from, from.plusDays(32)))
                .isEqualTo(EscalationRateGranularity.WEEK);
    }

    @Test
    void bucketsByWeekWhenUnbounded() {
        assertThat(JdbcDashboardRepository.resolveEscalationRateGranularity(null, null))
                .isEqualTo(EscalationRateGranularity.WEEK);
        assertThat(JdbcDashboardRepository.resolveEscalationRateGranularity(LocalDate.of(2026, 1, 1), null))
                .isEqualTo(EscalationRateGranularity.WEEK);
    }
}
