package com.coreeng.supportbot.teams;

import com.coreeng.supportbot.teams.groups.GroupRef;
import com.google.common.collect.ImmutableMap;
import java.util.List;

@FunctionalInterface
public interface PlatformTeamsFetcher {
    List<TeamAndGroupTuple> fetchTeams();

    /** @param attributes extra values about the team, keyed by attribute name; empty when the fetcher provides none */
    record TeamAndGroupTuple(String name, String code, GroupRef groupRef, ImmutableMap<String, String> attributes) {
        public TeamAndGroupTuple(String name, String code, GroupRef groupRef) {
            this(name, code, groupRef, ImmutableMap.of());
        }

        public TeamAndGroupTuple(String name, GroupRef groupRef) {
            this(name, name, groupRef);
        }

        public TeamAndGroupTuple(String name, String groupRef) {
            this(name, name, GroupRef.parse(groupRef));
        }
    }
}
