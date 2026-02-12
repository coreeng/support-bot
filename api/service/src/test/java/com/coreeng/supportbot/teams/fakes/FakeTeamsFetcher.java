package com.coreeng.supportbot.teams.fakes;

import com.coreeng.supportbot.teams.PlatformTeamsFetcher;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class FakeTeamsFetcher implements PlatformTeamsFetcher {
    private final List<TeamAndGroupTuple> teams;

    @Override
    public List<TeamAndGroupTuple> fetchTeams() {
        return teams;
    }
}
