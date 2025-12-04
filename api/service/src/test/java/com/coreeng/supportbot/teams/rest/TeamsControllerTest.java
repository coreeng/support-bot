package com.coreeng.supportbot.teams.rest;

import com.coreeng.supportbot.teams.SupportLeadershipTeamProps;
import com.coreeng.supportbot.teams.SupportTeamService;
import com.coreeng.supportbot.teams.Team;
import com.coreeng.supportbot.teams.TeamService;
import com.coreeng.supportbot.teams.TeamType;
import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TeamsControllerTest {

    private TeamService teamService;
    private SupportLeadershipTeamProps leadershipTeamProps;
    private SupportTeamService supportTeamService;
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
        when(support.types()).thenReturn(ImmutableList.of(TeamType.support));

        Team tenant = mock(Team.class);
        when(tenant.label()).thenReturn("tenant");
        when(tenant.types()).thenReturn(ImmutableList.of(TeamType.tenant));

        Team l2support = mock(Team.class);
        when(l2support.label()).thenReturn("l2support");
        when(l2support.types()).thenReturn(ImmutableList.of(TeamType.l2Support));

        when(teamService.listTeams()).thenReturn(ImmutableList.of(support, tenant, l2support));

        // when
        ImmutableList<TeamUI> result = controller.listTeams(null);

        // then
        assertThat(result).hasSize(3);
        assertThat(result.get(0).label()).isEqualTo("support");
        assertThat(result.get(0).types()).containsExactly(TeamType.support);
        assertThat(result.get(1).label()).isEqualTo("tenant");
        assertThat(result.get(1).types()).containsExactly(TeamType.tenant);
        assertThat(result.get(2).label()).isEqualTo("l2support");
        assertThat(result.get(2).types()).containsExactly(TeamType.l2Support);

        verify(teamService).listTeams();
        verifyNoMoreInteractions(teamService);
    }

    @Test
    void shouldListTeamsByTypeWhenTypeIsProvided() {
        // given
        Team team = mock(Team.class);
        when(team.label()).thenReturn("support");
        when(team.types()).thenReturn(ImmutableList.of(TeamType.support));

        when(teamService.listTeamsByType(TeamType.support)).thenReturn(ImmutableList.of(team));

        // when
        ImmutableList<TeamUI> result = controller.listTeams(TeamType.support);

        // then
        assertThat(result).hasSize(1);
        TeamUI ui = result.getFirst();
        assertThat(ui.label()).isEqualTo("support");
        assertThat(ui.types()).containsExactly(TeamType.support);

        verify(teamService).listTeamsByType(TeamType.support);
        verifyNoMoreInteractions(teamService);
    }
}
