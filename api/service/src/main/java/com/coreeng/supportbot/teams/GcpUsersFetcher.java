package com.coreeng.supportbot.teams;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.cloudidentity.v1.CloudIdentity;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class GcpUsersFetcher implements PlatformUsersFetcher {
    private final CloudIdentity cloudIdentity;

    @Override
    public List<Membership> fetchMembershipsByGroupRef(String groupRef) {
        ImmutableList.Builder<Membership> result = ImmutableList.builder();
        String groupId;
        try {
            var lookupResp =
                    cloudIdentity.groups().lookup().setGroupKeyId(groupRef).execute();
            groupId = lookupResp.getName();
        } catch (GoogleJsonResponseException e) {
            if (e.getStatusCode() == 403) {
                log.warn("Group is not found or not accessible: {}", groupRef);
                return ImmutableList.of();
            }
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try {
            var membershipResp = cloudIdentity
                    .groups()
                    .memberships()
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
        return result.build();
    }
}
