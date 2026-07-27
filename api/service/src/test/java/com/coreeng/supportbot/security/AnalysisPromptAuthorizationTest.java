package com.coreeng.supportbot.security;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.coreeng.supportbot.analysis.AnalysisService;
import com.coreeng.supportbot.analysis.rest.AnalysisController;
import com.coreeng.supportbot.teams.SupportTeamService;
import com.coreeng.supportbot.teams.Team;
import com.coreeng.supportbot.teams.TeamService;
import com.coreeng.supportbot.teams.TeamType;
import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@WebMvcTest(
        controllers = AnalysisController.class,
        properties = {
            "analysis.prompt.enabled=true",
            "security.jwt.secret=test-jwt-secret-for-unit-tests-minimum-256-bits",
            "security.test-bypass.enabled=false"
        })
@Import(SecurityConfig.class)
class AnalysisPromptAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnalysisService analysisService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private AuthCodeStore authCodeStore;

    @MockitoBean
    private TeamService teamService;

    @MockitoBean
    private SupportTeamService supportTeamService;

    @MockitoBean
    private OAuth2AvailabilityChecker oauth2AvailabilityChecker;

    @MockitoBean
    private AllowListService allowListService;

    @MockitoBean
    private JwtGroupTeamMerger jwtGroupTeamMerger;

    // Not a @MockitoBean: HandlerMappingIntrospector implements CorsConfigurationSource, so a
    // by-type mock override would replace that MVC bean and break the slice. Register a real
    // bean instead; the securityFilterChain parameter resolves to it by name.
    @TestConfiguration(proxyBeanMethods = false)
    static class CorsTestConfig {
        @Bean
        CorsConfigurationSource corsConfigurationSource() {
            return new UrlBasedCorsConfigurationSource();
        }
    }

    // The production filter chain authenticates with JwtAuthenticationToken; user(...) would
    // install a UsernamePasswordAuthenticationToken and could diverge from real behaviour.
    private static JwtAuthenticationToken authTokenWithRoles(Role... roles) {
        var principal = new UserPrincipal(
                "user@example.com",
                "Test User",
                ImmutableList.of(new Team("Test Tenant", "test-tenant", ImmutableList.of(TeamType.TENANT))),
                ImmutableList.copyOf(roles));
        return new JwtAuthenticationToken(principal, "test-token");
    }

    @Test
    void promptEndpoint_returnsPromptForSupportEngineers() throws Exception {
        when(analysisService.loadPrompt()).thenReturn("prompt text");

        mockMvc.perform(get("/analysis/prompt")
                        .with(authentication(authTokenWithRoles(Role.USER, Role.SUPPORT_ENGINEER))))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"prompt\":\"prompt text\"}"));
    }

    @Test
    void promptEndpoint_forbidsAuthenticatedUsersWithoutSupportEngineerRole() throws Exception {
        mockMvc.perform(get("/analysis/prompt").with(authentication(authTokenWithRoles(Role.USER))))
                .andExpect(status().isForbidden())
                .andExpect(content().json("{\"error\":\"Forbidden\"}"));
    }

    @Test
    void promptEndpoint_rejectsUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/analysis/prompt")).andExpect(status().isUnauthorized());
    }
}
