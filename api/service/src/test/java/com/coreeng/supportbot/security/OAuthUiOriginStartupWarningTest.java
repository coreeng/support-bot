package com.coreeng.supportbot.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

@ExtendWith(MockitoExtension.class)
class OAuthUiOriginStartupWarningTest {

    @Mock
    private Environment environment;

    @Mock
    private OAuth2AvailabilityChecker oauth2AvailabilityChecker;

    @BeforeEach
    void oauth2Configured() {
        when(oauth2AvailabilityChecker.isOAuth2Available()).thenReturn(true);
    }

    private OAuthUiOriginStartupWarning warning() {
        return new OAuthUiOriginStartupWarning(environment, oauth2AvailabilityChecker);
    }

    @Test
    void skipsPropertyLookupWhenLocalProfileActive() {
        when(environment.getActiveProfiles()).thenReturn(new String[] {"local"});
        warning().warnIfUiOriginUnsetOutsideLocalProfiles();
        verify(environment, never()).getProperty("UI_ORIGIN");
    }

    @Test
    void skipsPropertyLookupWhenSpringProfilesActiveUnset() {
        when(environment.getActiveProfiles()).thenReturn(new String[0]);
        when(environment.getProperty("spring.profiles.active")).thenReturn(null);
        warning().warnIfUiOriginUnsetOutsideLocalProfiles();
        verify(environment, never()).getProperty("UI_ORIGIN");
    }

    @Test
    void skipsWhenDeclaredDefaultButActiveProfilesEmpty() {
        when(environment.getActiveProfiles()).thenReturn(new String[0]);
        when(environment.getProperty("spring.profiles.active")).thenReturn("default");
        warning().warnIfUiOriginUnsetOutsideLocalProfiles();
        verify(environment, never()).getProperty("UI_ORIGIN");
    }

    @Test
    void skipsPropertyLookupWhenOnlyDefaultProfileActive() {
        when(environment.getActiveProfiles()).thenReturn(new String[] {"default"});
        warning().warnIfUiOriginUnsetOutsideLocalProfiles();
        verify(environment, never()).getProperty("UI_ORIGIN");
    }

    @Test
    void skipsPropertyLookupWhenTestProfileActive() {
        when(environment.getActiveProfiles()).thenReturn(new String[] {"test"});
        warning().warnIfUiOriginUnsetOutsideLocalProfiles();
        verify(environment, never()).getProperty("UI_ORIGIN");
    }

    @Test
    void skipsWhenOAuth2UnavailableWithoutReadingEnvironment() {
        when(oauth2AvailabilityChecker.isOAuth2Available()).thenReturn(false);
        warning().warnIfUiOriginUnsetOutsideLocalProfiles();
        verify(environment, never()).getProperty("UI_ORIGIN");
        verify(environment, never()).getActiveProfiles();
    }

    @Test
    void readsUiOriginWhenNonLocalProfile() {
        when(environment.getActiveProfiles()).thenReturn(new String[] {"production"});
        when(environment.getProperty("UI_ORIGIN")).thenReturn("https://ui.example.com");
        warning().warnIfUiOriginUnsetOutsideLocalProfiles();
        verify(environment).getProperty("UI_ORIGIN");
    }

    @Test
    void throwsWhenNonLocalProfileAndUiOriginUnset() {
        when(environment.getActiveProfiles()).thenReturn(new String[] {"production"});
        when(environment.getProperty("UI_ORIGIN")).thenReturn(null);
        assertThatThrownBy(() -> warning().warnIfUiOriginUnsetOutsideLocalProfiles())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("UI_ORIGIN is not set");
    }

    @Test
    void throwsWhenNonLocalProfileAndUiOriginBlank() {
        when(environment.getActiveProfiles()).thenReturn(new String[] {"production"});
        when(environment.getProperty("UI_ORIGIN")).thenReturn("  ");
        assertThatThrownBy(() -> warning().warnIfUiOriginUnsetOutsideLocalProfiles())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("UI_ORIGIN is not set");
    }

    @Test
    void throwsWhenDefaultAndNftBothActive() {
        when(environment.getActiveProfiles()).thenReturn(new String[] {"default", "nft"});
        when(environment.getProperty("UI_ORIGIN")).thenReturn(null);
        assertThatThrownBy(() -> warning().warnIfUiOriginUnsetOutsideLocalProfiles())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("UI_ORIGIN is not set");
    }
}
