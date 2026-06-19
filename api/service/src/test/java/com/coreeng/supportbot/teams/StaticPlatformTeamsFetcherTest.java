package com.coreeng.supportbot.teams;

import static org.assertj.core.api.Assertions.assertThat;

import com.coreeng.supportbot.teams.PlatformTeamsFetcher.TeamAndGroupTuple;
import com.coreeng.supportbot.teams.groups.GroupRef;
import java.util.List;
import org.junit.jupiter.api.Test;

class StaticPlatformTeamsFetcherTest {

    @Test
    void explicitCode_decouplesDisplayNameFromIdentity() {
        StaticPlatformTeamsProps props = new StaticPlatformTeamsProps(
                true,
                List.of(new StaticPlatformTeamsProps.TeamConfig("Display Name", "team-id", new GroupRef.Slack("S1"))));
        StaticPlatformTeamsFetcher fetcher = new StaticPlatformTeamsFetcher(props);

        List<TeamAndGroupTuple> result = fetcher.fetchTeams();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().name()).isEqualTo("Display Name");
        assertThat(result.getFirst().code()).isEqualTo("team-id");
    }

    @Test
    void missingCode_defaultsCodeToName() {
        StaticPlatformTeamsProps props = new StaticPlatformTeamsProps(
                true, List.of(new StaticPlatformTeamsProps.TeamConfig("platform", null, new GroupRef.Slack("S2"))));
        StaticPlatformTeamsFetcher fetcher = new StaticPlatformTeamsFetcher(props);

        List<TeamAndGroupTuple> result = fetcher.fetchTeams();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().name()).isEqualTo("platform");
        assertThat(result.getFirst().code()).isEqualTo("platform");
    }
}
