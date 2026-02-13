package com.coreeng.supportbot.security;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.coreeng.supportbot.teams.SupportTeamService;
import com.coreeng.supportbot.teams.Team;
import com.coreeng.supportbot.teams.TeamService;
import com.coreeng.supportbot.teams.TeamType;
import com.google.common.collect.ImmutableList;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;

@ExtendWith(MockitoExtension.class)
class OAuth2SuccessHandlerTest {

    private static final String TEST_SECRET = "test-jwt-secret-for-unit-tests-minimum-256-bits";

    @Mock
    private TeamService teamService;

    @Mock
    private SupportTeamService supportTeamService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    private OAuth2SuccessHandler createHandler() {
        var props = new SecurityProperties(
                new SecurityProperties.JwtProperties(TEST_SECRET, Duration.ofHours(24)),
                new SecurityProperties.OAuth2Properties("http://localhost:3000/auth/callback"),
                new SecurityProperties.CorsProperties(null),
                new SecurityProperties.TestBypassProperties(false),
                new SecurityProperties.AllowListProperties(List.of(), List.of()));
        var jwtService = new JwtService(props);
        var authCodeStore = new AuthCodeStore();
        return new OAuth2SuccessHandler(props, jwtService, authCodeStore, teamService, supportTeamService);
    }

    private Authentication mockAuth(Map<String, Object> attributes) {
        var oauth2User = mock(OAuth2User.class);
        when(oauth2User.getAttribute(anyString())).thenAnswer(inv -> attributes.get(inv.getArgument(0, String.class)));
        var authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(oauth2User);
        return authentication;
    }

    @Test
    void onAuthenticationSuccess_redirectsWithAuthCode() throws Exception {
        // given
        var handler = createHandler();
        when(teamService.listTeamsByUserEmail("user@example.com")).thenReturn(ImmutableList.of());
        var auth = mockAuth(Map.of("email", "user@example.com", "name", "Test User"));

        // when
        handler.onAuthenticationSuccess(request, response, auth);

        // then
        verify(response).sendRedirect(argThat(url -> url.startsWith("http://localhost:3000/auth/callback?code=")));
    }

    @Test
    void onAuthenticationSuccess_supportEngineerRole() throws Exception {
        // given
        var handler = createHandler();
        when(teamService.listTeamsByUserEmail("support@example.com"))
                .thenReturn(
                        ImmutableList.of(new Team("Core Support", "core-support", ImmutableList.of(TeamType.SUPPORT))));
        when(supportTeamService.isMemberByUserEmail("support@example.com")).thenReturn(true);
        var auth = mockAuth(Map.of("email", "support@example.com", "name", "Support User"));

        // when
        handler.onAuthenticationSuccess(request, response, auth);

        // then
        verify(response).sendRedirect(argThat(url -> {
            assertTrue(url.contains("code="));
            return true;
        }));
    }

    @Test
    void onAuthenticationSuccess_leadershipRole() throws Exception {
        // given
        var handler = createHandler();
        when(teamService.listTeamsByUserEmail("lead@example.com"))
                .thenReturn(
                        ImmutableList.of(new Team("Leadership", "leadership", ImmutableList.of(TeamType.LEADERSHIP))));
        when(supportTeamService.isLeadershipMemberByUserEmail("lead@example.com"))
                .thenReturn(true);
        var auth = mockAuth(Map.of("email", "lead@example.com", "name", "Lead User"));

        // when
        handler.onAuthenticationSuccess(request, response, auth);

        // then
        verify(response).sendRedirect(argThat(url -> url.contains("code=")));
    }

    @Test
    void onAuthenticationSuccess_extractsEmailFromPreferredUsername() throws Exception {
        // given
        var handler = createHandler();
        when(teamService.listTeamsByUserEmail("azureuser@example.com")).thenReturn(ImmutableList.of());
        var auth = mockAuth(Map.of("preferred_username", "azureuser@example.com", "name", "Azure User"));

        // when
        handler.onAuthenticationSuccess(request, response, auth);

        // then
        verify(teamService).listTeamsByUserEmail("azureuser@example.com");
        verify(response).sendRedirect(argThat(url -> url.contains("code=")));
    }
}
