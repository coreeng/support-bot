package com.coreeng.supportbot.teams;

import java.util.List;

@FunctionalInterface
public interface PlatformTeamsFetcher {
    List<TeamAndGroupTuple> fetchTeams();

    record TeamAndGroupTuple(String name, String groupRef) {}
}
