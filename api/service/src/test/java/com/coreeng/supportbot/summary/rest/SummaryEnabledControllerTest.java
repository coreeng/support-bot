package com.coreeng.supportbot.summary.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.coreeng.supportbot.config.LlmProps;
import com.coreeng.supportbot.config.LlmProvider;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class SummaryEnabledControllerTest {

    @Test
    void returnsEnabled_whenProviderSelected() {
        ResponseEntity<SummaryStatusUI> response = controllerWithEnabled(true).getSummaryEnabled();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().enabled()).isTrue();
    }

    @Test
    void returnsDisabled_whenNoProviderSelected() {
        // The point of the endpoint: with the feature off it must still answer, so the sidebar gets a
        // usable false instead of a 404 it would have to interpret.
        ResponseEntity<SummaryStatusUI> response = controllerWithEnabled(false).getSummaryEnabled();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().enabled()).isFalse();
    }

    private static SummaryEnabledController controllerWithEnabled(boolean enabled) {
        return new SummaryEnabledController(new LlmProps(
                enabled ? LlmProvider.VERTEX : LlmProvider.NONE,
                "gemini-2.5-flash",
                Duration.ofMillis(100),
                new LlmProps.Vertex("test-project", "europe-west2"),
                new LlmProps.Proxy("", new LlmProps.Proxy.Auth(""), Duration.ofSeconds(30)),
                new LlmProps.Stub(false)));
    }
}
