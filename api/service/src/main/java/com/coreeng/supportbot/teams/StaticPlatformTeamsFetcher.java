package com.coreeng.supportbot.teams;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class StaticPlatformTeamsFetcher implements PlatformTeamsFetcher {

    @Override
    public List<TeamAndGroupTuple> fetchTeams() {
        TeamAndGroupTuple t1 = new TeamAndGroupTuple("wow", "wow-group");
        TeamAndGroupTuple t2 = new TeamAndGroupTuple("infra-integration", "infra-group");
        TeamAndGroupTuple t3 = new TeamAndGroupTuple("connected-app", "connected-app-group");
        return List.of(t1, t2, t3);
    }
}
