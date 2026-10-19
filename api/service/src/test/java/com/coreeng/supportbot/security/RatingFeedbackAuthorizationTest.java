package com.coreeng.supportbot.security;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.coreeng.supportbot.rating.RatingFeedbackItem;
import com.coreeng.supportbot.rating.RatingService;
import com.coreeng.supportbot.rating.rest.RatingFeedbackController;
import com.coreeng.supportbot.teams.SupportTeamService;
import com.coreeng.supportbot.teams.Team;
import com.coreeng.supportbot.teams.TeamService;
import com.coreeng.supportbot.teams.TeamType;
import com.coreeng.supportbot.ticket.TicketId;
import com.google.common.collect.ImmutableList;
import java.time.LocalDate;
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
        controllers = RatingFeedbackController.class,
        properties = {
            "security.jwt.secret=test-jwt-secret-for-unit-tests-minimum-256-bits",
            "security.test-bypass.enabled=false"
        })
@Import(SecurityConfig.class)
class RatingFeedbackAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RatingService ratingService;

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

    // See AnalysisPromptAuthorizationTest for why this is a real bean rather than a @MockitoBean
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
    void feedback_isReturnedToSupportEngineers() throws Exception {
        when(ratingService.getFeedback(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31)))
                .thenReturn(ImmutableList.of(
                        new RatingFeedbackItem(5, LocalDate.of(2024, 1, 8), "Great", new TicketId(9))));

        mockMvc.perform(get("/rating/feedback")
                        .param("dateFrom", "2024-01-01")
                        .param("dateTo", "2024-01-31")
                        .with(authentication(authTokenWithRoles(Role.USER, Role.SUPPORT_ENGINEER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].feedback").value("Great"))
                .andExpect(jsonPath("$[0].weekStart").value("2024-01-08"))
                .andExpect(jsonPath("$[0].ticketId").value(9))
                // Anonymity: no rating id or exact timestamp leaves the API
                .andExpect(jsonPath("$[0].ratingId").doesNotExist())
                .andExpect(jsonPath("$[0].submittedTs").doesNotExist());
    }

    @Test
    void feedback_isReturnedToLeadership() throws Exception {
        when(ratingService.getFeedback(null, null)).thenReturn(ImmutableList.of());

        mockMvc.perform(get("/rating/feedback").with(authentication(authTokenWithRoles(Role.USER, Role.LEADERSHIP))))
                .andExpect(status().isOk());
    }

    @Test
    void feedback_isForbiddenForAuthenticatedUsersWithoutViewerRoles() throws Exception {
        mockMvc.perform(get("/rating/feedback").with(authentication(authTokenWithRoles(Role.USER))))
                .andExpect(status().isForbidden());
    }

    @Test
    void feedback_isForbiddenForEscalationUsers() throws Exception {
        mockMvc.perform(get("/rating/feedback").with(authentication(authTokenWithRoles(Role.USER, Role.ESCALATION))))
                .andExpect(status().isForbidden());
    }

    @Test
    void feedback_rejectsUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/rating/feedback")).andExpect(status().isUnauthorized());
    }
}
