package com.coreeng.supportbot.teams.fakes;

import com.coreeng.supportbot.teams.PlatformUsersFetcher;
import lombok.RequiredArgsConstructor;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public class FakeUsersFetcher implements PlatformUsersFetcher {
    private final Map<String, List<Membership>> membershipsByGroup;
    private final Map<String, Integer> calls = new ConcurrentHashMap<>();

    @Override
    public List<Membership> fetchMembershipsByGroupRef(String groupRef) {
        calls.merge(groupRef, 1, Integer::sum);
        return membershipsByGroup.getOrDefault(groupRef, Collections.emptyList());
    }

    public int getCallCount(String groupRef) {
        return calls.getOrDefault(groupRef, 0);
    }

    public int getTotalCalls() {
        return calls.values().stream().mapToInt(Integer::intValue).sum();
    }
}

