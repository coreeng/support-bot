package com.coreeng.supportbot.teams;

import com.coreeng.supportbot.teams.groups.GroupRef;
import java.util.List;

@FunctionalInterface
public interface PlatformTeamsFetcher {
    List<TeamAndGroupTuple> fetchTeams();

    record TeamAndGroupTuple(String name, GroupRef groupRef) {
        public TeamAndGroupTuple(String name, String groupRef) {
            this(name, GroupRef.parse(groupRef));
        }
    }
}
