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
        AnalysisProps.Vertex vertex =
                new AnalysisProps.Vertex("test-project", "europe-west2", "gemini-2.5-flash", Duration.ofMillis(100));
        AnalysisProps.Bundle bundle = new AnalysisProps.Bundle("classpath:placeholder-analysis-bundle.zip");
        AnalysisProps.Prompt prompt = new AnalysisProps.Prompt(enabled, "");
        AnalysisProps analysisProps = new AnalysisProps(vertex, bundle, prompt);
        return new AnalysisEnabledController(analysisProps);
    }
}
