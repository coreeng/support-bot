package com.coreeng.supportbot.security;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

@ExtendWith(MockitoExtension.class)
class OAuthUiOriginStartupWarningTest {

    @Mock
    private Environment environment;

    @Test
    void skipsPropertyLookupWhenLocalProfileActive() {
        when(environment.getActiveProfiles()).thenReturn(new String[] {"local"});
        new OAuthUiOriginStartupWarning(environment).warnIfUiOriginUnsetOutsideLocalProfiles();
        verify(environment, never()).getProperty("UI_ORIGIN");
    }

    @Test
    void readsUiOriginWhenNonLocalProfile() {
        when(environment.getActiveProfiles()).thenReturn(new String[] {"production"});
        when(environment.getProperty("UI_ORIGIN")).thenReturn("https://ui.example.com");
        new OAuthUiOriginStartupWarning(environment).warnIfUiOriginUnsetOutsideLocalProfiles();
        verify(environment).getProperty("UI_ORIGIN");
    }
}
