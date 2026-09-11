package com.coreeng.supportbot.analysis.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.coreeng.supportbot.config.LlmProps;
import com.coreeng.supportbot.config.LlmProvider;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class AnalysisEnabledControllerTest {

    @Test
    void returnsEnabled_whenProviderSelected() {
        AnalysisEnabledController controller = controllerWithEnabled(true);

        ResponseEntity<AnalysisEnabledController.FeatureStatus> response = controller.getAnalysisEnabled();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().enabled()).isTrue();
    }

    @Test
    void returnsDisabled_whenNoProviderSelected() {
        AnalysisEnabledController controller = controllerWithEnabled(false);

        ResponseEntity<AnalysisEnabledController.FeatureStatus> response = controller.getAnalysisEnabled();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().enabled()).isFalse();
    }

    private static AnalysisEnabledController controllerWithEnabled(boolean enabled) {
        LlmProps llmProps = new LlmProps(
                enabled ? LlmProvider.VERTEX : LlmProvider.NONE,
                "gemini-2.5-flash",
                Duration.ofMillis(100),
                new LlmProps.Vertex("test-project", "europe-west2"),
                new LlmProps.Proxy("", new LlmProps.Proxy.Auth(""), Duration.ofSeconds(30)),
                new LlmProps.Stub(false));
        return new AnalysisEnabledController(llmProps);
    }
}
