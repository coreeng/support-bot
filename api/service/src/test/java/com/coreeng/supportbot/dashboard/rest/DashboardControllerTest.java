package com.coreeng.supportbot.dashboard.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.coreeng.supportbot.dashboard.DashboardData.EscalationRate;
import com.coreeng.supportbot.dashboard.DashboardData.EscalationRateGranularity;
import com.coreeng.supportbot.dashboard.DashboardData.EscalationRatePoint;
import com.coreeng.supportbot.dashboard.DashboardData.EscalationTopic;
import com.coreeng.supportbot.dashboard.DashboardData.IncomingVsResolved;
import com.coreeng.supportbot.dashboard.DashboardData.IncomingVsResolvedGranularity;
import com.coreeng.supportbot.dashboard.DashboardData.IncomingVsResolvedRate;
import com.coreeng.supportbot.dashboard.DashboardData.ResolutionByEscalation;
import com.coreeng.supportbot.dashboard.DashboardData.TeamEscalationRate;
import com.coreeng.supportbot.dashboard.DashboardData.TeamScorecard;
import com.coreeng.supportbot.dashboard.DashboardData.TimeBeforeEscalation;
import com.coreeng.supportbot.dashboard.DashboardQueryService;
import com.coreeng.supportbot.dashboard.IncomingVsResolvedQuery;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class DashboardControllerTest {

    @Mock
    private DashboardQueryService dashboardQueryService;

    private DashboardController controller;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        controller = new DashboardController(dashboardQueryService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void getIncomingVsResolvedRate_forwardsExtendedParams() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 1, 31);
        List<String> teams = List.of("team-a", "team-b");
        IncomingVsResolvedQuery query =
                new IncomingVsResolvedQuery(from, to, true, teams, IncomingVsResolvedQuery.Granularity.AUTO);
        when(dashboardQueryService.getIncomingVsResolvedRate(query))
                .thenReturn(new IncomingVsResolvedRate(IncomingVsResolvedGranularity.DAY, List.of()));

        controller.getIncomingVsResolvedRate(teams, true, IncomingVsResolvedQuery.Granularity.AUTO, from, to);

        verify(dashboardQueryService).getIncomingVsResolvedRate(query);
    }

    @Test
    void getIncomingVsResolvedRate_reliesOnQueryObjectForNullTeamNormalization() {
        IncomingVsResolvedQuery query =
                new IncomingVsResolvedQuery(null, null, false, null, IncomingVsResolvedQuery.Granularity.AUTO);
        when(dashboardQueryService.getIncomingVsResolvedRate(query))
                .thenReturn(new IncomingVsResolvedRate(IncomingVsResolvedGranularity.DAY, List.of()));

        controller.getIncomingVsResolvedRate(null, null, IncomingVsResolvedQuery.Granularity.AUTO, null, null);

        verify(dashboardQueryService).getIncomingVsResolvedRate(query);
    }

    @Test
    void incomingVsResolvedRate_acceptsUppercaseGranularityBinding() throws Exception {
        when(dashboardQueryService.getIncomingVsResolvedRate(any()))
                .thenReturn(new IncomingVsResolvedRate(
                        IncomingVsResolvedGranularity.HOUR,
                        List.of(new IncomingVsResolved("2026-01-01T00:00:00Z", 2, 1))));

        mockMvc.perform(get("/dashboard/incoming-vs-resolved-rate")
                        .param("granularity", "AUTO")
                        .param("allTime", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.granularity").value("HOUR"))
                .andExpect(jsonPath("$.data[0].incoming").value(2));

        verify(dashboardQueryService).getIncomingVsResolvedRate(any());
    }

    @Test
    void escalationRate_forwardsDatesAndSerialisesPoints() throws Exception {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 1, 7);
        when(dashboardQueryService.getEscalationRate(from, to))
                .thenReturn(new EscalationRate(
                        EscalationRateGranularity.DAY, List.of(new EscalationRatePoint("2026-01-01", 4, 1, 0.25))));

        mockMvc.perform(get("/dashboard/escalation-rate")
                        .param("dateFrom", "2026-01-01")
                        .param("dateTo", "2026-01-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.granularity").value("DAY"))
                .andExpect(jsonPath("$.data[0].tickets").value(4))
                .andExpect(jsonPath("$.data[0].escalatedTickets").value(1))
                .andExpect(jsonPath("$.data[0].rate").value(0.25));
    }

    @Test
    void resolutionEscalatedVsNot_serialisesGroups() throws Exception {
        when(dashboardQueryService.getResolutionEscalatedVsNot(FROM, TO))
                .thenReturn(List.of(new ResolutionByEscalation(true, 2, 100.0, 200.0)));

        mockMvc.perform(withRange(get("/dashboard/resolution-escalated-vs-not")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].escalated").value(true))
                .andExpect(jsonPath("$[0].p90").value(200.0));
    }

    @Test
    void escalationTeamScorecard_serialisesMissingPercentilesAsNull() throws Exception {
        when(dashboardQueryService.getEscalationTeamScorecard(FROM, TO))
                .thenReturn(List.of(new TeamScorecard("team-a", 3, 3, null, null)));

        mockMvc.perform(withRange(get("/dashboard/escalation-team-scorecard")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].team").value("team-a"))
                .andExpect(jsonPath("$[0].open").value(3))
                .andExpect(jsonPath("$[0].resolutionP50").doesNotExist());
    }

    @Test
    void escalationTopics_serialisesRanking() throws Exception {
        when(dashboardQueryService.getEscalationTopics(FROM, TO))
                .thenReturn(List.of(new EscalationTopic("tag-a", 2, 50.0, 100.0, null)));

        mockMvc.perform(withRange(get("/dashboard/escalation-topics")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tag").value("tag-a"))
                .andExpect(jsonPath("$[0].totalSeconds").value(100.0))
                .andExpect(jsonPath("$[0].avgWaitSeconds").doesNotExist());
    }

    @Test
    void timeBeforeEscalation_serialisesPercentiles() throws Exception {
        when(dashboardQueryService.getTimeBeforeEscalation(FROM, TO))
                .thenReturn(new TimeBeforeEscalation(3, 3600.0, 6480.0));

        mockMvc.perform(withRange(get("/dashboard/time-before-escalation")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.escalations").value(3))
                .andExpect(jsonPath("$.p90").value(6480.0));
    }

    @Test
    void escalationRateByTeam_serialisesRows() throws Exception {
        when(dashboardQueryService.getEscalationRateByTeam(FROM, TO))
                .thenReturn(List.of(new TeamEscalationRate("team-a", 4, 1, 0.25)));

        mockMvc.perform(withRange(get("/dashboard/escalation-rate-by-team")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].team").value("team-a"))
                .andExpect(jsonPath("$[0].escalatedTickets").value(1))
                .andExpect(jsonPath("$[0].rate").value(0.25));
    }

    private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
    private static final LocalDate TO = LocalDate.of(2026, 1, 7);

    private static MockHttpServletRequestBuilder withRange(MockHttpServletRequestBuilder request) {
        return request.param("dateFrom", FROM.toString()).param("dateTo", TO.toString());
    }

    @Test
    void incomingVsResolvedRate_rejectsLowercaseGranularityBinding() throws Exception {
        mockMvc.perform(get("/dashboard/incoming-vs-resolved-rate").param("granularity", "auto"))
                .andExpect(status().isBadRequest());
    }
}
