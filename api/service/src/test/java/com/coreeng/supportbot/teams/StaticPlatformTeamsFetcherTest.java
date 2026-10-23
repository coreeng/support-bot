package com.coreeng.supportbot.teams;

import static org.assertj.core.api.Assertions.assertThat;

import com.coreeng.supportbot.teams.PlatformTeamsFetcher.TeamAndGroupTuple;
import com.coreeng.supportbot.teams.groups.GroupRef;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StaticPlatformTeamsFetcherTest {

    @Test
    void explicitCode_decouplesDisplayNameFromIdentity() {
        StaticPlatformTeamsProps props = new StaticPlatformTeamsProps(
                true,
                List.of(new StaticPlatformTeamsProps.TeamConfig(
                        "Display Name", "team-id", new GroupRef.Slack("S1"), null)));
        StaticPlatformTeamsFetcher fetcher = new StaticPlatformTeamsFetcher(props);

        List<TeamAndGroupTuple> result = fetcher.fetchTeams();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().name()).isEqualTo("Display Name");
        assertThat(result.getFirst().code()).isEqualTo("team-id");
    }

    @Test
    void missingCode_defaultsCodeToName() {
        StaticPlatformTeamsProps props = new StaticPlatformTeamsProps(
                true,
                List.of(new StaticPlatformTeamsProps.TeamConfig("platform", null, new GroupRef.Slack("S2"), null)));
        StaticPlatformTeamsFetcher fetcher = new StaticPlatformTeamsFetcher(props);

        List<TeamAndGroupTuple> result = fetcher.fetchTeams();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().name()).isEqualTo("platform");
        assertThat(result.getFirst().code()).isEqualTo("platform");
    }

    @Test
    void attributes_arePassedOn_leavingBlankValuesUnset() {
        StaticPlatformTeamsProps props = new StaticPlatformTeamsProps(
                true,
                List.of(new StaticPlatformTeamsProps.TeamConfig(
                        "Team A", "team-a", new GroupRef.Slack("S1"), Map.of("area", "Area A", "tier", " "))));

        List<TeamAndGroupTuple> result = new StaticPlatformTeamsFetcher(props).fetchTeams();

        assertThat(result.getFirst().attributes()).containsExactly(Map.entry("area", "Area A"));
    }

    @Test
    void noAttributes_meansNone() {
        StaticPlatformTeamsProps props = new StaticPlatformTeamsProps(
                true,
                List.of(new StaticPlatformTeamsProps.TeamConfig("Team A", "team-a", new GroupRef.Slack("S1"), null)));

        assertThat(new StaticPlatformTeamsFetcher(props).fetchTeams().getFirst().attributes())
                .isEmpty();
    }
}
