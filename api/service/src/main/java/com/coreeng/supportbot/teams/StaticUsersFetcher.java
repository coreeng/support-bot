package com.coreeng.supportbot.teams;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class StaticUsersFetcher implements PlatformUsersFetcher {

    private final StaticPlatformUsersProps props;

    @Override
    public List<Membership> fetchMembershipsByGroupRef(String groupRef) {
        return props.users().getOrDefault(groupRef, List.of()).stream()
                .map(Membership::new)
                .toList();
    }
}
