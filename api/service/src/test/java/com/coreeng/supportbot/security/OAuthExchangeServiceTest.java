package com.coreeng.supportbot.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.coreeng.supportbot.teams.SupportTeamService;
import com.coreeng.supportbot.teams.TeamService;
import com.google.common.collect.ImmutableList;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class OAuthExchangeServiceTest {

    private static final String VALID_REDIRECT_URI = "http://localhost:3000/api/oauth/callback/google";

    @Mock
    private ClientRegistrationRepository clientRegistrationRepository;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private TeamService teamService;

    @Mock
    private SupportTeamService supportTeamService;

    @Mock
    private JwtGroupTeamMerger jwtGroupTeamMerger;

    private final OAuthStateStore oauthStateStore = new OAuthStateStore();

    private String validState() {
        var state = java.util.UUID.randomUUID().toString();
        oauthStateStore.store(state);
        return state;
    }

    private OAuthExchangeService createService(List<String> allowedEmails, List<String> allowedDomains) {
        var props = new SecurityProperties(
                new SecurityProperties.JwtProperties(
                        "test-jwt-secret-for-unit-tests-minimum-256-bits", Duration.ofHours(1)),
                SecurityProperties.OAuth2Properties.withRedirectOnly("http://localhost:3000/login"),
                new SecurityProperties.CorsProperties(null),
                new SecurityProperties.TestBypassProperties(false),
                new SecurityProperties.AllowListProperties(allowedEmails, allowedDomains));
        var jwtService = new JwtService(props);
        var allowListService = new AllowListService(props);
        var redirectUriValidator = new RedirectUriValidator(props);
        lenient()
                .when(jwtGroupTeamMerger.mergeForProvider(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(2));
        return new OAuthExchangeService(
                clientRegistrationRepository,
                restTemplate,
                jwtService,
                teamService,
                supportTeamService,
                allowListService,
                jwtGroupTeamMerger,
                redirectUriValidator,
                oauthStateStore);
    }

    private void mockGoogleOAuth(String email) {
        var registration = ClientRegistration.withRegistrationId("google")
                .clientId("client-id")
                .clientSecret("client-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://accounts.google.com/o/oauth2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .userInfoUri("https://openidconnect.googleapis.com/v1/userinfo")
                .build();
        when(clientRegistrationRepository.findByRegistrationId("google")).thenReturn(registration);

        // Mock token exchange — no id_token so signature verification is not triggered
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(Map.of("access_token", "mock-access-token"));

        // Mock user info
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(Map.of("email", email, "name", "Test User"), HttpStatus.OK));
    }

    @Test
    void exchangeCodeForToken_userNotInAllowList_throws() {
        var service = createService(List.of(), List.of("allowed.com"));
        mockGoogleOAuth("user@blocked.com");

        assertThrows(
                UserNotAllowedException.class,
                () -> service.exchangeCodeForToken("google", "auth-code", VALID_REDIRECT_URI, validState()));
    }

    @Test
    void exchangeCodeForToken_userInAllowList_succeeds() {
        var service = createService(List.of(), List.of("allowed.com"));
        mockGoogleOAuth("user@allowed.com");
        when(teamService.listTeamsByUserEmail("user@allowed.com")).thenReturn(ImmutableList.of());

        var token = service.exchangeCodeForToken("google", "auth-code", VALID_REDIRECT_URI, validState());

        assertFalse(token == null || token.isBlank());
    }

    @Test
    void exchangeCodeForToken_emptyAllowList_allowsAll() {
        var service = createService(List.of(), List.of());
        mockGoogleOAuth("anyone@anywhere.com");
        when(teamService.listTeamsByUserEmail("anyone@anywhere.com")).thenReturn(ImmutableList.of());

        var token = service.exchangeCodeForToken("google", "auth-code", VALID_REDIRECT_URI, validState());

        assertFalse(token == null || token.isBlank());
    }

    @Test
    void exchangeCodeForToken_rejectsRedirectUriWithWrongOrigin() {
        var service = createService(List.of(), List.of());

        assertThrows(
                IllegalArgumentException.class,
                () -> service.exchangeCodeForToken(
                        "google", "auth-code", "https://evil.example/api/oauth/callback/google", validState()));
    }

    @Test
    void exchangeCodeForToken_rejectsRedirectUriWithWrongPath() {
        var service = createService(List.of(), List.of());

        assertThrows(
                IllegalArgumentException.class,
                () -> service.exchangeCodeForToken(
                        "google", "auth-code", "http://localhost:3000/some/other/path", validState()));
    }

    @Test
    void exchangeCodeForToken_rejectsInvalidState() {
        var service = createService(List.of(), List.of());

        assertThrows(
                IllegalArgumentException.class,
                () -> service.exchangeCodeForToken("google", "auth-code", VALID_REDIRECT_URI, "bogus-state"));
    }

    @Test
    void exchangeCodeForToken_rejectsNullState() {
        var service = createService(List.of(), List.of());

        assertThrows(
                IllegalArgumentException.class,
                () -> service.exchangeCodeForToken("google", "auth-code", VALID_REDIRECT_URI, null));
    }

    @Test
    void exchangeCodeForToken_stateIsOneTimeUse() {
        var service = createService(List.of(), List.of());
        mockGoogleOAuth("user@test.com");
        when(teamService.listTeamsByUserEmail("user@test.com")).thenReturn(ImmutableList.of());

        var state = validState();
        service.exchangeCodeForToken("google", "auth-code", VALID_REDIRECT_URI, state);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.exchangeCodeForToken("google", "auth-code", VALID_REDIRECT_URI, state));
    }
}
