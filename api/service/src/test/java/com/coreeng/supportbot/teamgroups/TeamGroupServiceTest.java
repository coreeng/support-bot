package com.coreeng.supportbot.teamgroups;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.coreeng.supportbot.config.TeamGroupsProps;
import com.coreeng.supportbot.teams.Team;
import com.coreeng.supportbot.teams.TeamDisplay;
import com.coreeng.supportbot.teams.TeamService;
import com.coreeng.supportbot.teams.TeamType;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TeamGroupServiceTest {

    private final TeamService teamService = mock(TeamService.class);

    @Test
    void listsTheGroupsInConfigurationOrderWithSortedMembers() {
        TeamGroupService service = service(
                new TeamGroupsProps.Group("zeta", "Zeta", List.of("team-z", "team-a")),
                new TeamGroupsProps.Group("alpha", "Alpha", List.of("team-b")));

        assertThat(service.list()).extracting(TeamGroup::code).containsExactly("zeta", "alpha");
        assertThat(service.list().getFirst().teamCodes()).containsExactly("team-a", "team-z");
    }

    @Test
    void findsAGroupByCode() {
        TeamGroupService service = service(new TeamGroupsProps.Group("area-a", "Area A", List.of("team-a")));

        assertThat(service.find("area-a"))
                .isNotNull()
                .extracting(TeamGroup::label)
                .isEqualTo("Area A");
        assertThat(service.find("area-b")).isNull();
    }

    @Test
    void describesMembersWithTheirDisplayLabels() {
        TeamGroupService service = service(new TeamGroupsProps.Group("area-a", "Area A", List.of("team-a", "gone")));
        when(teamService.resolveForDisplay("team-a"))
                .thenReturn(new TeamDisplay("team-a", "Team A", ImmutableList.of(TeamType.TENANT), true));
        when(teamService.resolveForDisplay("gone"))
                .thenReturn(new TeamDisplay("gone", "gone", ImmutableList.of(), false));

        assertThat(service.describe().getFirst().members())
                .containsExactly(
                        new TeamGroupService.Member("gone", "gone"), new TeamGroupService.Member("team-a", "Team A"));
    }

    @Test
    void unknownTeamCodesOnlyWarn() {
        // A retired team's code is still on its historical tickets; refusing to boot over it would
        // force operators to drop the history from the group.
        TeamGroupService service = service(new TeamGroupsProps.Group("area-a", "Area A", List.of("team-a", "retired")));
        when(teamService.listTeamsByType(TeamType.TENANT))
                .thenReturn(ImmutableList.of(new Team("Team A", "team-a", ImmutableList.of(TeamType.TENANT))));

        service.warnAboutUnknownTeams();
    }

    @Test
    void doesNotLookUpTeamsWhenNoGroupsAreConfigured() {
        TeamGroupService service = service();

        service.warnAboutUnknownTeams();

        assertThat(service.list()).isEmpty();
        assertThat(service.displayName()).isEqualTo("Area");
        verifyNoInteractions(teamService);
    }

    private TeamGroupService service(TeamGroupsProps.Group... groups) {
        TeamGroupsProps props = new TeamGroupsProps("Area", List.of(groups));
        return new TeamGroupService(new StaticTeamGroupProvider(props), props, teamService);
    }
}
