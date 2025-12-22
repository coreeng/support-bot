package com.coreeng.supportbot.teams.fakes;

import com.coreeng.supportbot.teams.PlatformUsersFetcher;
import lombok.Builder;
import lombok.Singular;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Builder
public class FakeUsersFetcher implements PlatformUsersFetcher {
    @Singular("memberships")
    private final Map<String, List<Membership>> membershipsByGroup;

    @Singular("failingGroup")
    private final Map<String, RuntimeException> failingGroups;

    private final Map<String, Integer> calls = new ConcurrentHashMap<>();

    @Override
    public List<Membership> fetchMembershipsByGroupRef(String groupRef) {
        calls.merge(groupRef, 1, Integer::sum);

        RuntimeException exception = failingGroups.get(groupRef);
        if (exception != null) {
            throw exception;
        }

        return membershipsByGroup.getOrDefault(groupRef, Collections.emptyList());
    }

    public int getCallCount(String groupRef) {
        return calls.getOrDefault(groupRef, 0);
    }

    public int getTotalCalls() {
        return calls.values().stream().mapToInt(Integer::intValue).sum();
    }
}

