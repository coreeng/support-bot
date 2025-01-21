package com.coreeng.supportbot.teams;

import com.google.api.services.cloudidentity.v1.CloudIdentity;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class GcpUsersFetcher implements UsersFetcher {
    private final CloudIdentity cloudIdentity;

    @Override
    public List<Membership> fetchMembershipsByGroupRef(String groupRef) {
        List<Membership> result = new ArrayList<>();
        try {
            var lookupResp = cloudIdentity.groups().lookup()
                .setGroupKeyId(groupRef)
                .execute();
            String groupId = lookupResp.getName();

            var membershipResp = cloudIdentity.groups().memberships()
                .searchTransitiveMemberships(groupId)
                .execute();
            for (var m : membershipResp.getMemberships()) {
                if (!m.getMember().startsWith("user")) {
                    continue;
                }
                String userEmail = m.getPreferredMemberKey().getFirst().getId();
                result.add(new Membership(userEmail));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return result;
    }
}
