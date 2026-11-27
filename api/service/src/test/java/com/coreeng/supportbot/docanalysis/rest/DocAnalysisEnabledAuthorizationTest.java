package com.coreeng.supportbot.docanalysis.rest;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.coreeng.supportbot.config.DocumentationAnalysisProps;
import com.coreeng.supportbot.config.LlmProps;
import com.coreeng.supportbot.security.AllowListService;
import com.coreeng.supportbot.security.AuthCodeStore;
import com.coreeng.supportbot.security.JwtAuthenticationToken;
import com.coreeng.supportbot.security.JwtGroupTeamMerger;
import com.coreeng.supportbot.security.JwtService;
import com.coreeng.supportbot.security.OAuth2AvailabilityChecker;
import com.coreeng.supportbot.security.Role;
import com.coreeng.supportbot.security.SecurityConfig;
import com.coreeng.supportbot.security.UserPrincipal;
import com.coreeng.supportbot.teams.SupportTeamService;
import com.coreeng.supportbot.teams.Team;
import com.coreeng.supportbot.teams.TeamService;
import com.coreeng.supportbot.teams.TeamType;
import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * The feature check is readable by any authenticated user, since the sidebar asks it for everyone,
 * unlike {@code GET /documentation-analysis}, which is for leadership and support engineers.
 */
@WebMvcTest(
        controllers = DocAnalysisEnabledController.class,
        properties = {
            "llm.provider=vertex",
            "llm.model-name=gemini-2.5-flash",
            "llm.vertex.project-id=test-project",
            "llm.vertex.location=europe-west2",
            "documentation-analysis.enabled=true",
            "documentation-analysis.source.github.repository=example-org/docs",
            "security.jwt.secret=test-jwt-secret-for-unit-tests-minimum-256-bits",
            "security.test-bypass.enabled=false"
        })
@Import(SecurityConfig.class)
// A @WebMvcTest slice does not run the application's @ConfigurationPropertiesScan, so the switches
// the controller reads have to be bound explicitly.
@EnableConfigurationProperties({LlmProps.class, DocumentationAnalysisProps.class})
class DocAnalysisEnabledAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

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
    // by-type mock override would replace that MVC bean and break the slice.
    @TestConfiguration(proxyBeanMethods = false)
    static class CorsTestConfig {
        @Bean
        CorsConfigurationSource corsConfigurationSource() {
            return new UrlBasedCorsConfigurationSource();
        }
    }

    private static JwtAuthenticationToken authTokenWithRoles(Role... roles) {
        var principal = new UserPrincipal(
                "user@example.com",
                "Test User",
                ImmutableList.of(new Team("Test Tenant", "test-tenant", ImmutableList.of(TeamType.TENANT))),
                ImmutableList.copyOf(roles));
        return new JwtAuthenticationToken(principal, "test-token");
    }

    @Test
    void answersAnyAuthenticatedUserWithoutRequiringAPageRole() throws Exception {
        for (Role[] roles :
                new Role[][] {{Role.USER}, {Role.USER, Role.LEADERSHIP}, {Role.USER, Role.SUPPORT_ENGINEER}}) {
            mockMvc.perform(get("/documentation-analysis/enabled").with(authentication(authTokenWithRoles(roles))))
                    .andExpect(status().isOk())
                    .andExpect(content().json("{\"enabled\":true}"));
        }
    }

    @Test
    void rejectsUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/documentation-analysis/enabled")).andExpect(status().isUnauthorized());
    }
}
