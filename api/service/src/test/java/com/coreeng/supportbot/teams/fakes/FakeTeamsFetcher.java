package com.coreeng.supportbot.teams.fakes;

import com.coreeng.supportbot.teams.PlatformTeamsFetcher;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public class FakeTeamsFetcher implements PlatformTeamsFetcher {
    private final List<TeamAndGroupTuple> teams;

    @Override
    public List<TeamAndGroupTuple> fetchTeams() {
        return teams;
    }
}

