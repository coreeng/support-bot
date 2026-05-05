package com.coreeng.supportbot.teams;

import com.coreeng.supportbot.teams.groups.GroupRef;
import java.util.List;

@FunctionalInterface
public interface PlatformTeamsFetcher {
    List<TeamAndGroupTuple> fetchTeams();

    record TeamAndGroupTuple(String name, GroupRef groupRef) {
        /** Convenience overload that parses {@code groupRef} via {@link GroupRef#parse(String)}. */
        public TeamAndGroupTuple(String name, String groupRef) {
            this(name, GroupRef.parse(groupRef));
        }
    }
}
