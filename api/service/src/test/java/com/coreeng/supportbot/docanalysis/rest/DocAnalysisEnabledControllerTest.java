package com.coreeng.supportbot.docanalysis.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.coreeng.supportbot.config.DocumentationAnalysisProps;
import com.coreeng.supportbot.config.LlmProps;
import com.coreeng.supportbot.config.LlmProvider;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.unit.DataSize;

class DocAnalysisEnabledControllerTest {

    @Test
    void isEnabledWhenAProviderIsSelectedAndTheFeatureSwitchedOn() {
        assertThat(enabled(LlmProvider.VERTEX, true)).isTrue();
    }

    @Test
    void isDisabledWhenTheFeatureIsNotSwitchedOn() {
        assertThat(enabled(LlmProvider.VERTEX, false)).isFalse();
    }

    @Test
    void isDisabledWithoutAProviderEvenWhenTheFeatureIsSwitchedOn() {
        assertThat(enabled(LlmProvider.NONE, true)).isFalse();
    }

    private static boolean enabled(LlmProvider provider, boolean featureEnabled) {
        ResponseEntity<DocAnalysisStatusUI> response = new DocAnalysisEnabledController(
                        llmProps(provider), props(featureEnabled))
                .getDocumentationAnalysisEnabled();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        return response.getBody().enabled();
    }

    private static LlmProps llmProps(LlmProvider provider) {
        return new LlmProps(
                provider,
                "gemini-2.5-flash",
                Duration.ofMillis(100),
                new LlmProps.Vertex("test-project", "europe-west2"),
                new LlmProps.Proxy("", new LlmProps.Proxy.Auth(""), Duration.ofSeconds(30)),
                new LlmProps.Anthropic("", "", Duration.ofSeconds(60), 16000),
                new LlmProps.Stub(false));
    }

    private static DocumentationAnalysisProps props(boolean enabled) {
        return new DocumentationAnalysisProps(
                enabled,
                150_000,
                500,
                Duration.ofHours(24),
                3,
                Duration.ofMinutes(15),
                new DocumentationAnalysisProps.Source(
                        "github",
                        new DocumentationAnalysisProps.GitHub(
                                "example-org/docs",
                                "main",
                                "docs",
                                List.of(".md"),
                                "/tmp/documentation-analysis",
                                Duration.ofHours(24),
                                DataSize.ofMegabytes(50),
                                5000,
                                "whole",
                                2)));
    }
}
