package com.coreeng.supportbot.prtracking.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.coreeng.supportbot.config.PrTrackingProps;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TenantInsightsEnabledControllerTest {

    @Mock
    private PrTrackingProps prTrackingProps;

    @Test
    void returnsEnabled_whenPrTrackingEnabled() {
        when(prTrackingProps.enabled()).thenReturn(true);
        var controller = new TenantInsightsEnabledController(prTrackingProps);

        assertThat(controller.enabled().enabled()).isTrue();
    }

    @Test
    void returnsDisabled_whenPrTrackingDisabled() {
        when(prTrackingProps.enabled()).thenReturn(false);
        var controller = new TenantInsightsEnabledController(prTrackingProps);

        assertThat(controller.enabled().enabled()).isFalse();
    }
}
