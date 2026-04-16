package com.coreeng.supportbot.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
        when(environment.getProperty("spring.profiles.active")).thenReturn("local");
        when(environment.getActiveProfiles()).thenReturn(new String[] {"local"});
        new OAuthUiOriginStartupWarning(environment).warnIfUiOriginUnsetOutsideLocalProfiles();
        verify(environment, never()).getProperty("UI_ORIGIN");
    }

    @Test
    void skipsPropertyLookupWhenSpringProfilesActiveUnset() {
        when(environment.getProperty("spring.profiles.active")).thenReturn(null);
        when(environment.getActiveProfiles()).thenReturn(new String[] {"default"});
        new OAuthUiOriginStartupWarning(environment).warnIfUiOriginUnsetOutsideLocalProfiles();
        verify(environment, never()).getProperty("UI_ORIGIN");
    }

    @Test
    void skipsPropertyLookupWhenOnlyDefaultProfileActive() {
        when(environment.getProperty("spring.profiles.active")).thenReturn("default");
        when(environment.getActiveProfiles()).thenReturn(new String[] {"default"});
        new OAuthUiOriginStartupWarning(environment).warnIfUiOriginUnsetOutsideLocalProfiles();
        verify(environment, never()).getProperty("UI_ORIGIN");
    }

    @Test
    void skipsPropertyLookupWhenTestProfileActive() {
        when(environment.getProperty("spring.profiles.active")).thenReturn("test");
        when(environment.getActiveProfiles()).thenReturn(new String[] {"test"});
        new OAuthUiOriginStartupWarning(environment).warnIfUiOriginUnsetOutsideLocalProfiles();
        verify(environment, never()).getProperty("UI_ORIGIN");
    }

    @Test
    void readsUiOriginWhenNonLocalProfile() {
        when(environment.getProperty("spring.profiles.active")).thenReturn("production");
        when(environment.getActiveProfiles()).thenReturn(new String[] {"production"});
        when(environment.getProperty("UI_ORIGIN")).thenReturn("https://ui.example.com");
        new OAuthUiOriginStartupWarning(environment).warnIfUiOriginUnsetOutsideLocalProfiles();
        verify(environment).getProperty("UI_ORIGIN");
    }

    @Test
    void throwsWhenNonLocalProfileAndUiOriginUnset() {
        when(environment.getProperty("spring.profiles.active")).thenReturn("production");
        when(environment.getActiveProfiles()).thenReturn(new String[] {"production"});
        when(environment.getProperty("UI_ORIGIN")).thenReturn(null);
        assertThatThrownBy(() -> new OAuthUiOriginStartupWarning(environment).warnIfUiOriginUnsetOutsideLocalProfiles())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("UI_ORIGIN is not set");
    }

    @Test
    void throwsWhenNonLocalProfileAndUiOriginBlank() {
        when(environment.getProperty("spring.profiles.active")).thenReturn("production");
        when(environment.getActiveProfiles()).thenReturn(new String[] {"production"});
        when(environment.getProperty("UI_ORIGIN")).thenReturn("  ");
        assertThatThrownBy(() -> new OAuthUiOriginStartupWarning(environment).warnIfUiOriginUnsetOutsideLocalProfiles())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("UI_ORIGIN is not set");
    }
}
