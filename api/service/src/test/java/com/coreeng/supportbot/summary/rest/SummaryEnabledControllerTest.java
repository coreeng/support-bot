package com.coreeng.supportbot.summary.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.coreeng.supportbot.config.SummaryProps;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class SummaryEnabledControllerTest {

    @Test
    void returnsEnabled_whenSummaryEnabled() {
        ResponseEntity<SummaryStatusUI> response = controllerWithEnabled(true).getSummaryEnabled();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().enabled()).isTrue();
    }

    @Test
    void returnsDisabled_whenSummaryDisabled() {
        // The point of the endpoint: with the feature off it must still answer, so the sidebar gets a
        // usable false instead of a 404 it would have to interpret.
        ResponseEntity<SummaryStatusUI> response = controllerWithEnabled(false).getSummaryEnabled();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().enabled()).isFalse();
    }

    private static SummaryEnabledController controllerWithEnabled(boolean enabled) {
        return new SummaryEnabledController(new SummaryProps(enabled, 400, Duration.ofMinutes(15)));
    }
}
