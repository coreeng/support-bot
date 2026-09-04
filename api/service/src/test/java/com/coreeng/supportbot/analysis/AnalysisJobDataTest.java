package com.coreeng.supportbot.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AnalysisJobDataTest {

    @Test
    void roundTripsADaysRun() {
        assertThat(AnalysisJobData.parse(AnalysisJobData.days(7))).isEqualTo(new AnalysisJobData.DaysRun(7));
    }

    @Test
    void roundTripsAWindowRun() {
        String data = AnalysisJobData.window(LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 23));

        assertThat(data).isEqualTo("window:2026-03-10:2026-03-23");
        assertThat(AnalysisJobData.parse(data))
                .isEqualTo(new AnalysisJobData.WindowRun(LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 23)));
    }

    @Test
    void stillReadsTheBarePayloadWrittenBeforeWindowsExisted() {
        // Rows written by an older version of the service must survive a rolling upgrade; the
        // alternative is the resume deleting them and silently dropping an in-flight run.
        assertThat(AnalysisJobData.parse("30")).isEqualTo(new AnalysisJobData.DaysRun(30));
        assertThat(AnalysisJobData.parse(" 30 ")).isEqualTo(new AnalysisJobData.DaysRun(30));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "   ",
                "nonsense",
                "window:",
                "window:2026-03-10",
                "window:2026-03-10:2026-03-23:extra",
                "window:not-a-date:2026-03-23",
                "window:2026-03-10:2026-13-45"
            })
    void returnsNullRatherThanThrowingOnAnythingElse(String data) {
        // The only caller is the startup resume: a payload it cannot read must be cleaned up, never
        // crash the boot.
        assertThat(AnalysisJobData.parse(data)).isNull();
    }
}
