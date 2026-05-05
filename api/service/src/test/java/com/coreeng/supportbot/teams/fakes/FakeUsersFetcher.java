package com.coreeng.supportbot.teams.fakes;

import com.coreeng.supportbot.teams.PlatformUsersFetcher;
import com.coreeng.supportbot.teams.groups.GroupRef;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Builder;
import lombok.Singular;

/**
 * Test fake for {@link PlatformUsersFetcher}, typed for {@link GroupRef.Static}. Tests pass it
 * through the {@code staticFetcher} slot of {@link com.coreeng.supportbot.teams.groups.GroupResolver}
 * — most test group refs (e.g. {@code "wow-group"}) parse as {@code Static}.
 */
@Builder
public class FakeUsersFetcher implements PlatformUsersFetcher<GroupRef.Static> {
    @Singular("memberships")
    private final Map<String, List<Membership>> membershipsByGroup;

    @Singular("failingGroup")
    private final Map<String, RuntimeException> failingGroups;

    private final Map<String, Integer> calls = new ConcurrentHashMap<>();

    @Override
    public List<Membership> fetchMembershipsByGroupRef(GroupRef.Static groupRef) {
        String key = groupRef.value();
        calls.merge(key, 1, Integer::sum);

        RuntimeException exception = failingGroups.get(key);
        if (exception != null) {
            throw exception;
        }

        return membershipsByGroup.getOrDefault(key, Collections.emptyList());
    }

    public int getCallCount(String groupRef) {
        return calls.getOrDefault(groupRef, 0);
    }

    public int getTotalCalls() {
        return calls.values().stream().mapToInt(Integer::intValue).sum();
    }
}
