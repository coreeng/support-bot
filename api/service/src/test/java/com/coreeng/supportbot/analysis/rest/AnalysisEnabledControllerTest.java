package com.coreeng.supportbot.analysis.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.coreeng.supportbot.config.AnalysisProps;
import com.coreeng.supportbot.knowledgegaps.rest.KnowledgeGapsStatusUI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class AnalysisEnabledControllerTest {

    @Test
    void returnsEnabled_whenAnalysisPromptEnabled() {
        AnalysisEnabledController controller = controllerWithEnabled(true);

        ResponseEntity<KnowledgeGapsStatusUI> response = controller.getAnalysisEnabled();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().enabled()).isTrue();
    }

    @Test
    void returnsDisabled_whenAnalysisPromptDisabled() {
        AnalysisEnabledController controller = controllerWithEnabled(false);

        ResponseEntity<KnowledgeGapsStatusUI> response = controller.getAnalysisEnabled();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().enabled()).isFalse();
    }

    private static AnalysisEnabledController controllerWithEnabled(boolean enabled) {
        AnalysisProps.Llm llm = new AnalysisProps.Llm(
                "gemini-2.5-flash",
                Duration.ofMillis(100),
                new AnalysisProps.Vertex(true, "test-project", "europe-west2"),
                new AnalysisProps.Proxy(false, "", new AnalysisProps.Proxy.Auth(""), Duration.ofSeconds(30)),
                new AnalysisProps.GoogleAi(false, ""));
        AnalysisProps.Bundle bundle = new AnalysisProps.Bundle("classpath:placeholder-analysis-bundle.zip");
        AnalysisProps.Prompt prompt = new AnalysisProps.Prompt(enabled);
        AnalysisProps analysisProps = new AnalysisProps(llm, bundle, prompt);
        return new AnalysisEnabledController(analysisProps);
    }
}
