package com.coreeng.supportbot.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coreeng.supportbot.config.AnalysisProps.Bundle;
import com.coreeng.supportbot.config.AnalysisProps.Llm;
import com.coreeng.supportbot.config.AnalysisProps.Prompt;
import com.coreeng.supportbot.config.AnalysisProps.Proxy;
import com.coreeng.supportbot.config.AnalysisProps.Stub;
import com.coreeng.supportbot.config.AnalysisProps.Vertex;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class SummaryValidationConfigTest {

    @Test
    void refusesToStartWithTheSummaryPageOnAndAnalysisOff() {
        assertThatThrownBy(() -> new SummaryValidationConfig(new SummaryProps(true, 400), analysisProps(false)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("summary.enabled=true requires analysis.prompt.enabled=true");
    }

    @Test
    void allowsTheSupportedCombinations() {
        assertThatCode(() -> new SummaryValidationConfig(new SummaryProps(true, 400), analysisProps(true)))
                .doesNotThrowAnyException();
        assertThatCode(() -> new SummaryValidationConfig(new SummaryProps(false, 400), analysisProps(false)))
                .doesNotThrowAnyException();
        // Analysis without the summary page is the pre-existing deployment shape and must stay valid.
        assertThatCode(() -> new SummaryValidationConfig(new SummaryProps(false, 400), analysisProps(true)))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsANonsensicalReasonLimitWhenEnabled() {
        assertThatThrownBy(() -> new SummaryProps(true, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("summary.max-reasons");
    }

    private static AnalysisProps analysisProps(boolean promptEnabled) {
        Llm llm = new Llm(
                "gemini-2.5-flash",
                Duration.ofMillis(100),
                new Vertex(true, "test-project", "europe-west2"),
                new Proxy(false, "", new Proxy.Auth(""), Duration.ofSeconds(30)),
                new Stub(false));
        return new AnalysisProps(
                llm, new Bundle("classpath:placeholder-analysis-bundle.zip"), new Prompt(promptEnabled));
    }
}
