package com.coreeng.supportbot.teamgroups;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.coreeng.supportbot.config.TeamGroupsProps;
import com.coreeng.supportbot.teams.Team;
import com.coreeng.supportbot.teams.TeamService;
import com.coreeng.supportbot.teams.TeamType;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AttributeTeamGroupProviderTest {

    private final TeamService teamService = mock(TeamService.class);

    @Test
    void groupsTeamsByTheAttributesValue_inLabelOrder() {
        givenTeams(
                team("team-c", "area", "Area B"), team("team-a", "area", "Area A"), team("team-b", "area", "Area A"));

        ImmutableList<TeamGroup> groups = provider("area").groups();

        assertThat(groups)
                .extracting(TeamGroup::code, TeamGroup::label)
                .containsExactly(tuple("area-a", "Area A"), tuple("area-b", "Area B"));
        assertThat(groups.getFirst().teamCodes()).containsExactly("team-a", "team-b");
    }

    @Test
    void leavesTeamsWithoutAUsableValueOutOfEveryGroup() {
        givenTeams(
                team("team-a", "area", "Area A"),
                team("no-value", "tier", "gold"),
                team("blank", "area", "  "),
                team("symbols-only", "area", "--"));

        ImmutableList<TeamGroup> groups = provider("area").groups();

        assertThat(groups).singleElement().satisfies(group -> assertThat(group.teamCodes())
                .containsExactly("team-a"));
    }

    @Test
    void valuesSpelledDifferently_shareOneGroup_labelledWithTheFirstSpellingInSortOrder() {
        givenTeams(team("team-a", "area", "area a"), team("team-b", "area", "Area A"));

        ImmutableList<TeamGroup> groups = provider("area").groups();

        assertThat(groups).singleElement().satisfies(group -> {
            assertThat(group.code()).isEqualTo("area-a");
            assertThat(group.label()).isEqualTo("Area A");
            assertThat(group.teamCodes()).containsExactly("team-a", "team-b");
        });
    }

    @Test
    void noAttributeConfigured_meansNoGroups_withoutLookingAtTeams() {
        assertThat(provider(null).groups()).isEmpty();
        verifyNoInteractions(teamService);
    }

    @Test
    void noTeamCarryingTheAttribute_meansNoGroups() {
        givenTeams(team("team-a", "tier", "gold"));

        assertThat(provider("area").groups()).isEmpty();
    }

    @Test
    void buildsTheGroupsOnce() {
        givenTeams(team("team-a", "area", "Area A"));
        AttributeTeamGroupProvider provider = provider("area");

        provider.groups();
        provider.groups();

        verify(teamService, times(1)).listTeamsByType(TeamType.TENANT);
    }

    @Test
    void buildsTheGroupsAtStartup_andServesThemFromThenOn() {
        givenTeams(team("team-a", "area", "Area A"));
        AttributeTeamGroupProvider provider = provider("area");

        provider.buildAtStartup();
        verify(teamService, times(1)).listTeamsByType(TeamType.TENANT);

        assertThat(provider.groups())
                .singleElement()
                .extracting(TeamGroup::code)
                .isEqualTo("area-a");
        verify(teamService, times(1)).listTeamsByType(TeamType.TENANT);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "Area A|area-a",
                "  Data & Analytics  |data-analytics",
                "Área Ñ|area-n",
                "area_1|area-1",
                "--x--|x",
                "***|''"
            })
    void derivesAUrlSafeCode(String value, String code) {
        assertThat(AttributeTeamGroupProvider.toCode(value)).isEqualTo(code);
        if (!code.isEmpty()) {
            assertThat(code).matches(TeamGroup.CODE_PATTERN);
        }
    }

    @Test
    void capsTheCodeAtSixtyFourCharacters_withoutATrailingHyphen() {
        String code = AttributeTeamGroupProvider.toCode("a".repeat(63) + " b");

        assertThat(code).isEqualTo("a".repeat(63)).matches(TeamGroup.CODE_PATTERN);
    }

    private AttributeTeamGroupProvider provider(@Nullable String attribute) {
        return new AttributeTeamGroupProvider(new TeamGroupsProps("Area", attribute), teamService);
    }

    private void givenTeams(Team... teams) {
        when(teamService.listTeamsByType(TeamType.TENANT)).thenReturn(ImmutableList.copyOf(teams));
    }

    private static Team team(String code, String attribute, String value) {
        return new Team(code, code, ImmutableList.of(TeamType.TENANT), ImmutableMap.of(attribute, value));
    }
}
