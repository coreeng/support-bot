package com.coreeng.supportbot.teams;

import com.coreeng.supportbot.teams.groups.GroupRef;
import java.util.List;

public interface PlatformUsersFetcher<R extends GroupRef> {
    GroupRef.Provider provider();

    /**
     * If the group doesn't exist, return an empty membership list
     */
    List<Membership> fetchMembershipsByGroupRef(R groupRef);

    record Membership(String email) {}
}
