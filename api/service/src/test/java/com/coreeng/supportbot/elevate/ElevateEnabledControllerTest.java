package com.coreeng.supportbot.elevate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ElevateEnabledControllerTest {

    @Mock
    private ElevateQueryService elevateQueryService;

    @Test
    void returnsEnabled_whenElevateConfigured() {
        when(elevateQueryService.configured()).thenReturn(true);
        var controller = new ElevateEnabledController(elevateQueryService);

        assertThat(controller.enabled().enabled()).isTrue();
    }

    @Test
    void returnsDisabled_whenElevateNotConfigured() {
        when(elevateQueryService.configured()).thenReturn(false);
        var controller = new ElevateEnabledController(elevateQueryService);

        assertThat(controller.enabled().enabled()).isFalse();
    }
}
