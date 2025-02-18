package com.coreeng.supportbot.teams;

import java.util.List;

public interface PlatformUsersFetcher {
    /**
     * If the group doesn't exist, return an empty membership list
     */
    List<Membership> fetchMembershipsByGroupRef(String groupRef);

    record Membership(
        String email
    ) {
    }
}
