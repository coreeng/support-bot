package com.coreeng.supportbot.dashboard.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.coreeng.supportbot.dashboard.DashboardData.IncomingVsResolved;
import com.coreeng.supportbot.dashboard.DashboardData.IncomingVsResolvedGranularity;
import com.coreeng.supportbot.dashboard.DashboardData.IncomingVsResolvedRate;
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
    void incomingVsResolvedRate_rejectsLowercaseGranularityBinding() throws Exception {
        mockMvc.perform(get("/dashboard/incoming-vs-resolved-rate").param("granularity", "auto"))
                .andExpect(status().isBadRequest());
    }
}
