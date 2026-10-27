package com.coreeng.supportbot.teamgroups;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.coreeng.supportbot.config.TeamGroupsProps;
import com.coreeng.supportbot.teams.TeamDisplay;
import com.coreeng.supportbot.teams.TeamService;
import com.coreeng.supportbot.teams.TeamType;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;
import org.junit.jupiter.api.Test;

class TeamGroupServiceTest {

    private final TeamService teamService = mock(TeamService.class);

    @Test
    void findsAGroupByCode() {
        TeamGroupService service = service(group("area-a", "Area A", "team-a"));

        assertThat(service.find("area-a"))
                .isNotNull()
                .extracting(TeamGroup::label)
                .isEqualTo("Area A");
        assertThat(service.find("area-b")).isNull();
    }

    @Test
    void refusesToPickBetweenGroupsSharingACode() {
        TeamGroupService service = service(group("area-a", "Area A", "team-a"), group("area-a", "Area A2", "team-b"));

        assertThatThrownBy(() -> service.find("area-a")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void describesMembersWithTheirDisplayLabels() {
        TeamGroupService service = service(group("area-a", "Area A", "team-a", "team-b"));
        when(teamService.resolveForDisplay("team-a"))
                .thenReturn(new TeamDisplay("team-a", "Team A", ImmutableList.of(TeamType.TENANT), true));
        when(teamService.resolveForDisplay("team-b"))
                .thenReturn(new TeamDisplay("team-b", "Team B", ImmutableList.of(TeamType.TENANT), true));

        assertThat(service.describe().getFirst().members())
                .containsExactly(
                        new TeamGroupService.Member("team-a", "Team A"),
                        new TeamGroupService.Member("team-b", "Team B"));
    }

    @Test
    void usesTheConfiguredDisplayName() {
        TeamGroupService service =
                new TeamGroupService(ImmutableList::of, new TeamGroupsProps("Domain", null), teamService);

        assertThat(service.displayName()).isEqualTo("Domain");
        assertThat(service.list()).isEmpty();
    }

    private TeamGroupService service(TeamGroup... groups) {
        return new TeamGroupService(
                () -> ImmutableList.copyOf(groups), new TeamGroupsProps("Area", "area"), teamService);
    }

    private static TeamGroup group(String code, String label, String... teamCodes) {
        return new TeamGroup(code, label, ImmutableSortedSet.copyOf(teamCodes));
    }
}
