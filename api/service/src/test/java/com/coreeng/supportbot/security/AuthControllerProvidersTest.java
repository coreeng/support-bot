package com.coreeng.supportbot.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class AuthControllerProvidersTest {

    @Mock
    private OAuth2AvailabilityChecker oauth2AvailabilityChecker;

    @Mock
    private AuthCodeStore authCodeStore;

    @Mock
    private OAuthUrlService oauthUrlService;

    @Mock
    private OAuthExchangeService oauthExchangeService;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller =
                new AuthController(authCodeStore, oauthUrlService, oauthExchangeService, oauth2AvailabilityChecker);
    }

    @Test
    void shouldReturnProviders_whenBothConfigured() {
        // given
        when(oauth2AvailabilityChecker.getAvailableProviders()).thenReturn(List.of("google", "azure"));

        // when
        var response = controller.getAvailableProviders();

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().providers()).containsExactly("google", "azure");
    }

    @Test
    void shouldReturnGoogleOnly_whenOnlyGoogleConfigured() {
        // given
        when(oauth2AvailabilityChecker.getAvailableProviders()).thenReturn(List.of("google"));

        // when
        var response = controller.getAvailableProviders();

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().providers()).containsExactly("google");
    }

    @Test
    void shouldReturnAzureOnly_whenOnlyAzureConfigured() {
        // given
        when(oauth2AvailabilityChecker.getAvailableProviders()).thenReturn(List.of("azure"));

        // when
        var response = controller.getAvailableProviders();

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().providers()).containsExactly("azure");
    }

    @Test
    void shouldReturnEmptyArray_whenNoProvidersConfigured() {
        // given
        when(oauth2AvailabilityChecker.getAvailableProviders()).thenReturn(List.of());

        // when
        var response = controller.getAvailableProviders();

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().providers()).isEmpty();
    }
}
