package com.coreeng.supportbot.prtracking.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.coreeng.supportbot.config.PrTrackingProps;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class TenantInsightsEnabledControllerTest {

    @Test
    void returnsEnabled_whenPrTrackingEnabled() {
        TenantInsightsEnabledController controller = controllerWithEnabled(true);

        ResponseEntity<TenantInsightsEnabledController.FeatureStatus> response = controller.enabled();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().enabled()).isTrue();
    }

    @Test
    void returnsDisabled_whenPrTrackingDisabled() {
        TenantInsightsEnabledController controller = controllerWithEnabled(false);

        ResponseEntity<TenantInsightsEnabledController.FeatureStatus> response = controller.enabled();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().enabled()).isFalse();
    }

    private static TenantInsightsEnabledController controllerWithEnabled(boolean enabled) {
        PrTrackingProps props = new PrTrackingProps(
                enabled,
                "0 0 9-18 * * 1-5",
                "pr",
                enabled ? List.of("tag") : List.of(),
                enabled ? "Information Request" : "",
                "hours",
                enabled
                        ? List.of(new PrTrackingProps.Repository(
                                "my-org/onboarding-repo",
                                "wow",
                                new PrTrackingProps.Sla(null, Duration.ofDays(2), null)))
                        : List.of(),
                enabled
                        ? new PrTrackingProps.GitHub(
                                PrTrackingProps.AuthMode.TOKEN, "https://api.github.com", "pat-123", "", "", "")
                        : null,
                null);
        return new TenantInsightsEnabledController(props);
    }
}
