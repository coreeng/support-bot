package com.coreeng.supportbot.teams;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class StaticUsersFetcher implements PlatformUsersFetcher {

    @Override
    public List<Membership> fetchMembershipsByGroupRef(String groupRef) {
        if ("wow-group".equals(groupRef)) {
            return List.of(new Membership("savvas.michael@cecg.io"));
        } else {
            return List.of(new Membership("test1@test.com"), new Membership("test2@test.com"), new Membership("test3@test.com"));
        }
    }
}