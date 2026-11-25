package com.coreeng.supportbot.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.coreeng.supportbot.dashboard.DashboardData.Granularity;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class GranularityTest {

    @Test
    void bucketsByDayUpToAMonth() {
        LocalDate from = LocalDate.of(2026, 1, 1);

        assertThat(Granularity.dayOrWeekForRange(from, from.plusDays(7))).isEqualTo(Granularity.DAY);
        assertThat(Granularity.dayOrWeekForRange(from, from.plusDays(31))).isEqualTo(Granularity.DAY);
    }

    @Test
    void bucketsByWeekBeyondAMonth() {
        LocalDate from = LocalDate.of(2026, 1, 1);

        assertThat(Granularity.dayOrWeekForRange(from, from.plusDays(32))).isEqualTo(Granularity.WEEK);
    }

    @Test
    void bucketsByWeekWhenUnbounded() {
        assertThat(Granularity.dayOrWeekForRange(null, null)).isEqualTo(Granularity.WEEK);
        assertThat(Granularity.dayOrWeekForRange(LocalDate.of(2026, 1, 1), null))
                .isEqualTo(Granularity.WEEK);
    }

    @Test
    void unitIsThePostgresTruncationUnit() {
        assertThat(Granularity.HOUR.unit()).isEqualTo("hour");
        assertThat(Granularity.DAY.unit()).isEqualTo("day");
        assertThat(Granularity.WEEK.unit()).isEqualTo("week");
    }
}
