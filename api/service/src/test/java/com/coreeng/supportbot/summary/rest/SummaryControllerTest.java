package com.coreeng.supportbot.summary.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.coreeng.supportbot.security.AllowListService;
import com.coreeng.supportbot.security.AuthCodeStore;
import com.coreeng.supportbot.security.JwtAuthenticationToken;
import com.coreeng.supportbot.security.JwtGroupTeamMerger;
import com.coreeng.supportbot.security.JwtService;
import com.coreeng.supportbot.security.OAuth2AvailabilityChecker;
import com.coreeng.supportbot.security.Role;
import com.coreeng.supportbot.security.SecurityConfig;
import com.coreeng.supportbot.security.UserPrincipal;
import com.coreeng.supportbot.summary.SummaryBreakdowns;
import com.coreeng.supportbot.summary.SummaryService;
import com.coreeng.supportbot.summary.SummaryState;
import com.coreeng.supportbot.summary.SummaryWindow;
import com.coreeng.supportbot.teams.SupportTeamService;
import com.coreeng.supportbot.teams.Team;
import com.coreeng.supportbot.teams.TeamService;
import com.coreeng.supportbot.teams.TeamType;
import com.google.common.collect.ImmutableList;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
        controllers = SummaryController.class,
        properties = {
            "summary.enabled=true",
            "analysis.prompt.enabled=true",
            "security.jwt.secret=test-jwt-secret-for-unit-tests-minimum-256-bits",
            "security.test-bypass.enabled=false"
        })
@Import({SecurityConfig.class, SummaryMapper.class, SummaryExceptionHandler.class})
class SummaryControllerTest {

    /** A Thursday, so "yesterday" is unambiguous in the assertions below. */
    private static final Instant NOW = Instant.parse("2026-03-26T09:15:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SummaryService summaryService;

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
    static class TestBeans {
        @Bean
        CorsConfigurationSource corsConfigurationSource() {
            return new UrlBasedCorsConfigurationSource();
        }

        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
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
    void defaultsToTheLastFourteenDaysEndingYesterday() throws Exception {
        // Today is unfinished, so including it would move the window — and invalidate the cached
        // summary — on every visit.
        givenSummary();

        mockMvc.perform(get("/summary").with(authentication(authTokenWithRoles(Role.USER, Role.LEADERSHIP))))
                .andExpect(status().isOk());

        ArgumentCaptor<LocalDate> from = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> to = ArgumentCaptor.forClass(LocalDate.class);
        verify(summaryService).get(from.capture(), to.capture());
        assertThat(to.getValue()).isEqualTo(LocalDate.of(2026, 3, 25));
        assertThat(from.getValue()).isEqualTo(LocalDate.of(2026, 3, 12));
    }

    @Test
    void serialisesTheBreakdownsAndTheSummarySection() throws Exception {
        givenSummary();

        mockMvc.perform(get("/summary?from=2026-03-10&to=2026-03-23")
                        .with(authentication(authTokenWithRoles(Role.USER, Role.SUPPORT_ENGINEER))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-03-10"))
                .andExpect(jsonPath("$.to").value("2026-03-23"))
                .andExpect(jsonPath("$.totalTickets").value(3))
                .andExpect(jsonPath("$.unclassifiedTickets").value(1))
                .andExpect(jsonPath("$.teams[0].label").value("team-a"))
                .andExpect(jsonPath("$.teams[0].recent").isEmpty())
                .andExpect(jsonPath("$.drivers[0].label").value("Knowledge Gap"))
                .andExpect(jsonPath("$.drivers[0].recent[0].ticketId").value("42"))
                .andExpect(jsonPath("$.drivers[0].recent[0].text").value("Did not know pipelines existed."))
                .andExpect(jsonPath("$.drivers[0].recent[0].timestamp").value("1970-01-01T00:00:00Z"))
                .andExpect(jsonPath("$.knowledgeGaps[0].label").value("Build & CI"))
                .andExpect(jsonPath("$.knowledgeGaps[0].count").value(2))
                .andExpect(jsonPath("$.products[0].label").value("Alpha"))
                .andExpect(jsonPath("$.summary.state").value("ready"))
                .andExpect(jsonPath("$.summary.content").value("the prose"))
                .andExpect(jsonPath("$.summary.progress").doesNotExist());
    }

    @Test
    void rejectsAWindowThatEndsBeforeItStarts() throws Exception {
        mockMvc.perform(get("/summary?from=2026-03-23&to=2026-03-10")
                        .with(authentication(authTokenWithRoles(Role.USER, Role.LEADERSHIP))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SUMMARY_WINDOW_INVALID"));

        verify(summaryService, never()).get(any(), any());
    }

    @Test
    void rejectsAWindowWiderThanAYear() throws Exception {
        mockMvc.perform(get("/summary?from=2020-01-01&to=2026-01-01")
                        .with(authentication(authTokenWithRoles(Role.USER, Role.LEADERSHIP))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SUMMARY_WINDOW_INVALID"));
    }

    @Test
    void forbidsAuthenticatedUsersWithoutLeadershipOrSupportEngineer() throws Exception {
        // Serving this triggers a backfill server-side, so the role check is the only gate on that
        // work — a plain user must not be able to start it.
        mockMvc.perform(get("/summary").with(authentication(authTokenWithRoles(Role.USER))))
                .andExpect(status().isForbidden());

        verify(summaryService, never()).get(any(), any());
    }

    @Test
    void rejectsUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/summary")).andExpect(status().isUnauthorized());
    }

    private void givenSummary() {
        SummaryWindow window = new SummaryWindow(LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 23));
        SummaryBreakdowns breakdowns = new SummaryBreakdowns(
                window,
                3,
                2,
                ImmutableList.of(new com.coreeng.supportbot.summary.SummaryCount(
                        "Knowledge Gap",
                        2,
                        ImmutableList.of(new com.coreeng.supportbot.summary.SummaryTicketExample(
                                42L, "Did not know pipelines existed.", Instant.EPOCH)))),
                ImmutableList.of(),
                ImmutableList.of(new com.coreeng.supportbot.summary.SummaryCount("Build & CI", 2)),
                ImmutableList.of(),
                ImmutableList.of(new com.coreeng.supportbot.summary.SummaryCount("team-a", 3)),
                ImmutableList.of(new com.coreeng.supportbot.summary.SummaryCount("Alpha", 1)));
        when(summaryService.get(any(), any()))
                .thenReturn(new SummaryService.SummaryResult(
                        breakdowns, new SummaryState.Ready("the prose", "model-a", Instant.EPOCH)));
    }
}
