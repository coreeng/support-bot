package com.coreeng.supportbot.teams;

import java.util.List;

public interface PlatformUsersFetcher {
    List<Membership> fetchMembershipsByGroupRef(String groupRef);

    record Membership(
        String email
    ) {
    }
}
