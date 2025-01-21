package com.coreeng.supportbot.teams;

import java.util.List;

public interface UsersFetcher {
    List<Membership> fetchMembershipsByGroupRef(String groupRef);

    record Membership(
        String email
    ) {
    }
}
