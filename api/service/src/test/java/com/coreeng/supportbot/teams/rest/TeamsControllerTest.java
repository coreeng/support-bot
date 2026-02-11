package com.coreeng.supportbot.teams.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.coreeng.supportbot.teams.Team;
import com.coreeng.supportbot.teams.TeamService;
import com.coreeng.supportbot.teams.TeamType;
import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TeamsControllerTest {

    private TeamService teamService;
    private TeamsController controller;

    @BeforeEach
    void setUp() {
        teamService = mock(TeamService.class);
        TeamUIMapper mapper = new TeamUIMapper(); // real mapper is simple and stateless
        controller = new TeamsController(teamService, mapper);
    }

    @Test
    void shouldListAllTeamsWhenTypeIsNull() {
        // given
        Team support = mock(Team.class);

        when(support.label()).thenReturn("support");
        when(support.types()).thenReturn(ImmutableList.of(TeamType.SUPPORT));

        Team tenant = mock(Team.class);
        when(tenant.label()).thenReturn("tenant");
        when(tenant.types()).thenReturn(ImmutableList.of(TeamType.TENANT));

        Team escalation = mock(Team.class);
        when(escalation.label()).thenReturn("escalation");
        when(escalation.types()).thenReturn(ImmutableList.of(TeamType.ESCALATION));

        when(teamService.listTeams()).thenReturn(ImmutableList.of(support, tenant, escalation));

        // when
        ImmutableList<TeamUI> result = controller.listTeams(null);

        // then
        assertThat(result).hasSize(3);
        assertThat(result.get(0).label()).isEqualTo("support");
        assertThat(result.get(0).types()).containsExactly(TeamType.SUPPORT);
        assertThat(result.get(1).label()).isEqualTo("tenant");
        assertThat(result.get(1).types()).containsExactly(TeamType.TENANT);
        assertThat(result.get(2).label()).isEqualTo("escalation");
        assertThat(result.get(2).types()).containsExactly(TeamType.ESCALATION);

        verify(teamService).listTeams();
        verifyNoMoreInteractions(teamService);
    }

    @Test
    void shouldListTeamsByTypeWhenTypeIsProvided() {
        // given
        Team team = mock(Team.class);
        when(team.label()).thenReturn("support");
        when(team.types()).thenReturn(ImmutableList.of(TeamType.SUPPORT));

        when(teamService.listTeamsByType(TeamType.SUPPORT)).thenReturn(ImmutableList.of(team));

        // when
        ImmutableList<TeamUI> result = controller.listTeams(TeamType.SUPPORT);

        // then
        assertThat(result).hasSize(1);
        TeamUI ui = result.getFirst();
        assertThat(ui.label()).isEqualTo("support");
        assertThat(ui.types()).containsExactly(TeamType.SUPPORT);

        verify(teamService).listTeamsByType(TeamType.SUPPORT);
        verifyNoMoreInteractions(teamService);
    }
}
