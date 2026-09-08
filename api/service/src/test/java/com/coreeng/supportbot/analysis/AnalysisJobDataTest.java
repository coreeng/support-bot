package com.coreeng.supportbot.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AnalysisJobDataTest {

    @Test
    void encodesADaysRunAsJson() {
        assertThat(AnalysisJobData.days(7)).isEqualTo("{\"type\":\"days\",\"days\":7}");
        assertThat(AnalysisJobData.parse(AnalysisJobData.days(7))).isEqualTo(new AnalysisJobData.DaysRun(7));
    }

    @Test
    void encodesAWindowRunAsJson() {
        String data = AnalysisJobData.window(LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 23));

        assertThat(data).isEqualTo("{\"type\":\"window\",\"from\":\"2026-03-10\",\"to\":\"2026-03-23\"}");
        assertThat(AnalysisJobData.parse(data))
                .isEqualTo(new AnalysisJobData.WindowRun(LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 23)));
    }

    @Test
    void stillReadsTheBareIntegerWrittenBeforeTheJsonForm() {
        // Rows written by an older version of the service must survive a rolling upgrade; the
        // alternative is the resume deleting them and silently dropping an in-flight run.
        assertThat(AnalysisJobData.parse("30")).isEqualTo(new AnalysisJobData.DaysRun(30));
        assertThat(AnalysisJobData.parse(" 30 ")).isEqualTo(new AnalysisJobData.DaysRun(30));
    }

    @Test
    void toleratesFieldsItDoesNotKnow() {
        // A newer version may add a field; rolling back to this one must not delete its in-flight run.
        assertThat(AnalysisJobData.parse("{\"type\":\"days\",\"days\":7,\"requestedBy\":\"alex\"}"))
                .isEqualTo(new AnalysisJobData.DaysRun(7));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "   ",
                "nonsense",
                "{}",
                "{\"type\":\"days\"}",
                "{\"type\":\"nope\",\"days\":7}",
                "{\"type\":\"days\",\"days\":0}",
                "0",
                "-3",
                "{\"type\":\"window\",\"from\":\"2026-03-10\"}",
                "{\"type\":\"window\",\"from\":\"not-a-date\",\"to\":\"2026-03-23\"}",
                "{\"type\":\"window\",\"from\":\"2026-03-10\",\"to\":\"2026-13-45\"}",
                "{\"type\":\"days\",\"days\":7",
                "window:2026-03-10:2026-03-23"
            })
    void returnsNullRatherThanThrowingOnAnythingElse(String data) {
        // The only caller is the startup resume: a payload it cannot read must be cleaned up, never
        // crash the boot.
        assertThat(AnalysisJobData.parse(data)).isNull();
    }
}
