package com.coreeng.supportbot.teams;

import java.util.List;

public interface TeamsFetcher {
    List<TeamAndGroupTuple> fetchTeams();

    record TeamAndGroupTuple(
        String name,
        String groupRef
    ) {
    }
}
