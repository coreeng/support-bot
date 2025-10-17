package com.coreeng.supportbot.teams;

import com.microsoft.graph.models.UserCollectionResponse;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Requires Azure permissions: GroupMember.Read.All, User.ReadBasic.All
 */
@Slf4j
@RequiredArgsConstructor
public class AzureUsersFetcher implements PlatformUsersFetcher {
    private final GraphServiceClient graphClient;

    @Override
    public List<Membership> fetchMembershipsByGroupRef(String groupRef) {
        UserCollectionResponse response = graphClient.groups().byGroupId(groupRef)
            .transitiveMembers().graphUser().get(req -> {
                requireNonNull(req.queryParameters);
                req.queryParameters.select = new String[]{"mail", "accountEnabled", "deletedDateTime"};
            });
        requireNonNull(response);
        requireNonNull(response.getValue());
        return response.getValue().stream()
            .filter(v -> v.getDeletedDateTime() == null
                && v.getAccountEnabled() != null && v.getAccountEnabled()
                && v.getMail() != null && !v.getMail().isBlank())
            .map(v -> new Membership(v.getMail()))
            .toList();
    }
}
