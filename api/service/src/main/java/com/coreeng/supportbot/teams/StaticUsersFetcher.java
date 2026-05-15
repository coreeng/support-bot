package com.coreeng.supportbot.teams;

import com.coreeng.supportbot.teams.groups.GroupRef;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class StaticUsersFetcher implements PlatformUsersFetcher<GroupRef.Static> {

    private final StaticPlatformUsersProps props;

    @Override
    public List<Membership> fetchMembershipsByGroupRef(GroupRef.Static groupRef) {
        return props.users().getOrDefault(groupRef.key(), List.of()).stream()
                .map(Membership::new)
                .toList();
    }
}
