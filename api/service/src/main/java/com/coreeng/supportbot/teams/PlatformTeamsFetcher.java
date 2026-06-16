package com.coreeng.supportbot.teams;

import com.coreeng.supportbot.teams.groups.GroupRef;
import java.util.List;

@FunctionalInterface
public interface PlatformTeamsFetcher {
    List<TeamAndGroupTuple> fetchTeams();

    record TeamAndGroupTuple(String name, String code, GroupRef groupRef) {
        public TeamAndGroupTuple(String name, GroupRef groupRef) {
            this(name, name, groupRef);
        }

        public TeamAndGroupTuple(String name, String groupRef) {
            this(name, name, GroupRef.parse(groupRef));
        }
    }
}
