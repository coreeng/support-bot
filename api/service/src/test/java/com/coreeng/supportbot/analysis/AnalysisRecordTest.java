package com.coreeng.supportbot.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.coreeng.supportbot.analysis.AnalysisRepository.AnalysisRecord;
import org.junit.jupiter.api.Test;

class AnalysisRecordTest {

    @Test
    void isValid_shouldReturnTrueForCompleteRecord() {
        // given
        AnalysisRecord record = new AnalysisRecord(
                1, "Knowledge Gap", "Monitoring & Troubleshooting", "workload compute", "Summary text", "prompt-v1");

        // when
        boolean result = record.isValid();

        // then
        assertThat(result).isTrue();
    }

    @Test
    void isValid_shouldReturnFalseWhenTicketIdIsZero() {
        // given
        AnalysisRecord record = new AnalysisRecord(
                0, "Knowledge Gap", "Monitoring & Troubleshooting", "workload compute", "Summary text", "prompt-v1");

        // when
        boolean result = record.isValid();

        // then
        assertThat(result).isFalse();
    }

    @Test
    void isValid_shouldReturnFalseWhenTicketIdIsNegative() {
        // given
        AnalysisRecord record = new AnalysisRecord(
                -1, "Knowledge Gap", "Monitoring & Troubleshooting", "workload compute", "Summary text", "prompt-v1");

        // when
        boolean result = record.isValid();

        // then
        assertThat(result).isFalse();
    }

    @Test
    void isValid_shouldReturnFalseWhenDriverIsNull() {
        // given
        AnalysisRecord record = new AnalysisRecord(
                1, null, "Monitoring & Troubleshooting", "workload compute", "Summary text", "prompt-v1");

        // when
        boolean result = record.isValid();

        // then
        assertThat(result).isFalse();
    }

    @Test
    void isValid_shouldReturnFalseWhenDriverIsBlank() {
        // given
        AnalysisRecord record = new AnalysisRecord(
                1, "  ", "Monitoring & Troubleshooting", "workload compute", "Summary text", "prompt-v1");

        // when
        boolean result = record.isValid();

        // then
        assertThat(result).isFalse();
    }

    @Test
    void isValid_shouldReturnFalseWhenCategoryIsNull() {
        // given
        AnalysisRecord record =
                new AnalysisRecord(1, "Knowledge Gap", null, "workload compute", "Summary text", "prompt-v1");

        // when
        boolean result = record.isValid();

        // then
        assertThat(result).isFalse();
    }

    @Test
    void isValid_shouldReturnFalseWhenCategoryIsBlank() {
        // given
        AnalysisRecord record =
                new AnalysisRecord(1, "Knowledge Gap", "", "workload compute", "Summary text", "prompt-v1");

        // when
        boolean result = record.isValid();

        // then
        assertThat(result).isFalse();
    }

    @Test
    void isValid_shouldReturnFalseWhenFeatureIsNull() {
        // given
        AnalysisRecord record = new AnalysisRecord(
                1, "Knowledge Gap", "Monitoring & Troubleshooting", null, "Summary text", "prompt-v1");

        // when
        boolean result = record.isValid();

        // then
        assertThat(result).isFalse();
    }

    @Test
    void isValid_shouldReturnFalseWhenFeatureIsBlank() {
        // given
        AnalysisRecord record = new AnalysisRecord(
                1, "Knowledge Gap", "Monitoring & Troubleshooting", "   ", "Summary text", "prompt-v1");

        // when
        boolean result = record.isValid();

        // then
        assertThat(result).isFalse();
    }

    @Test
    void isValid_shouldReturnFalseWhenSummaryIsNull() {
        // given
        AnalysisRecord record = new AnalysisRecord(
                1, "Knowledge Gap", "Monitoring & Troubleshooting", "workload compute", null, "prompt-v1");

        // when
        boolean result = record.isValid();

        // then
        assertThat(result).isFalse();
    }

    @Test
    void isValid_shouldReturnFalseWhenSummaryIsBlank() {
        // given
        AnalysisRecord record = new AnalysisRecord(
                1, "Knowledge Gap", "Monitoring & Troubleshooting", "workload compute", "\t\n", "prompt-v1");

        // when
        boolean result = record.isValid();

        // then
        assertThat(result).isFalse();
    }

    @Test
    void isValid_shouldAllowNullPromptId() {
        // given - promptId can be null (it's set by the caller after LLM analysis)
        AnalysisRecord record = new AnalysisRecord(
                1, "Knowledge Gap", "Monitoring & Troubleshooting", "workload compute", "Summary text", null);

        // when
        boolean result = record.isValid();

        // then
        assertThat(result).isTrue();
    }

    @Test
    void isValid_shouldReturnTrueWithMinimalValidData() {
        // given
        AnalysisRecord record = new AnalysisRecord(1, "Bug", "Config", "net", "Issue", "v1");

        // when
        boolean result = record.isValid();

        // then
        assertThat(result).isTrue();
    }

    @Test
    void isValid_shouldHandleWhitespaceInValidFields() {
        // given - fields with actual content plus whitespace should be valid
        AnalysisRecord record =
                new AnalysisRecord(1, " Knowledge Gap ", " Category ", " Feature ", " Summary ", "prompt-v1");

        // when
        boolean result = record.isValid();

        // then
        assertThat(result).isTrue();
    }
}
