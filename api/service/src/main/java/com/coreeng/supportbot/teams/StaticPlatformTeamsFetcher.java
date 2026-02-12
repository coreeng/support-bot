package com.coreeng.supportbot.teams;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class StaticPlatformTeamsFetcher implements PlatformTeamsFetcher {

    private final StaticPlatformTeamsProps props;

    @Override
    public List<TeamAndGroupTuple> fetchTeams() {
        return props.teams().stream()
                .map(team -> new TeamAndGroupTuple(team.name(), team.groupRef()))
                .toList();
    }
}
