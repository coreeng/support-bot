package com.coreeng.supportbot.security;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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

    @Mock
    private ClientRegistrationRepository clientRegistrationRepository;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private TeamService teamService;

    @Mock
    private SupportTeamService supportTeamService;

    private OAuthExchangeService createService(List<String> allowedEmails, List<String> allowedDomains) {
        var props = new SecurityProperties(
                new SecurityProperties.JwtProperties(
                        "test-jwt-secret-for-unit-tests-minimum-256-bits", Duration.ofHours(1)),
                new SecurityProperties.OAuth2Properties("http://localhost:3000/login"),
                new SecurityProperties.CorsProperties(null),
                new SecurityProperties.TestBypassProperties(false),
                new SecurityProperties.AllowListProperties(allowedEmails, allowedDomains));
        var jwtService = new JwtService(props);
        var allowListService = new AllowListService(props);
        return new OAuthExchangeService(
                clientRegistrationRepository,
                restTemplate,
                jwtService,
                teamService,
                supportTeamService,
                allowListService);
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

        // Mock token exchange
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(Map.of("access_token", "mock-access-token"));

        // Mock user info
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(Map.of("email", email, "name", "Test User"), HttpStatus.OK));
    }

    @Test
    void exchangeCodeForToken_userNotInAllowList_throws() {
        // given — only allowed.com domain
        var service = createService(List.of(), List.of("allowed.com"));
        mockGoogleOAuth("user@blocked.com");

        // when/then
        assertThrows(
                UserNotAllowedException.class,
                () -> service.exchangeCodeForToken("google", "auth-code", "http://localhost:3000/callback"));
    }

    @Test
    void exchangeCodeForToken_userInAllowList_succeeds() {
        // given — allowed.com domain
        var service = createService(List.of(), List.of("allowed.com"));
        mockGoogleOAuth("user@allowed.com");
        when(teamService.listTeamsByUserEmail("user@allowed.com")).thenReturn(ImmutableList.of());

        // when — should not throw
        var token = service.exchangeCodeForToken("google", "auth-code", "http://localhost:3000/callback");

        // then
        assert token != null && !token.isBlank();
    }

    @Test
    void exchangeCodeForToken_emptyAllowList_allowsAll() {
        // given — no restrictions
        var service = createService(List.of(), List.of());
        mockGoogleOAuth("anyone@anywhere.com");
        when(teamService.listTeamsByUserEmail("anyone@anywhere.com")).thenReturn(ImmutableList.of());

        // when — should not throw
        var token = service.exchangeCodeForToken("google", "auth-code", "http://localhost:3000/callback");

        // then
        assert token != null && !token.isBlank();
    }
}
