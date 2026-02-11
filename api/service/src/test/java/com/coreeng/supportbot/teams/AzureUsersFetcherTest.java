package com.coreeng.supportbot.teams;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.microsoft.graph.groups.GroupsRequestBuilder;
import com.microsoft.graph.groups.item.GroupItemRequestBuilder;
import com.microsoft.graph.groups.item.transitivemembers.TransitiveMembersRequestBuilder;
import com.microsoft.graph.groups.item.transitivemembers.graphuser.GraphUserRequestBuilder;
import com.microsoft.graph.models.User;
import com.microsoft.graph.models.UserCollectionResponse;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AzureUsersFetcherTest {
    @Test
    void fetchMembershipsByGroupRef_shouldReturnFilteredMemberships() {
        // Given
        GraphServiceClient mockGraphClient = mock(GraphServiceClient.class);
        GroupsRequestBuilder groupsRequestBuilder = mock(GroupsRequestBuilder.class);
        GroupItemRequestBuilder groupRequestBuilder = mock(GroupItemRequestBuilder.class);
        TransitiveMembersRequestBuilder membersRequestBuilder = mock(TransitiveMembersRequestBuilder.class);
        GraphUserRequestBuilder graphUserRequestBuilder = mock(GraphUserRequestBuilder.class);
        UserCollectionResponse mockResponse = new UserCollectionResponse();

        User user1 = new User();
        user1.setMail("user1@example.com");
        user1.setAccountEnabled(true);
        user1.setDeletedDateTime(null);

        User user2 = new User();
        user2.setMail("user2@example.com");
        user2.setAccountEnabled(true);
        user2.setDeletedDateTime(null);

        User user3 = new User();
        user3.setMail(null);
        user3.setAccountEnabled(true);
        user3.setDeletedDateTime(null);

        User user4 = new User();
        user4.setMail("user4@example.com");
        user4.setAccountEnabled(false);
        user4.setDeletedDateTime(null);

        User user5 = new User();
        user5.setMail("user5@example.com");
        user5.setAccountEnabled(true);
        user5.setDeletedDateTime(OffsetDateTime.now(ZoneId.systemDefault()));

        mockResponse.setValue(List.of(user1, user2, user3));

        when(mockGraphClient.groups()).thenReturn(groupsRequestBuilder);
        when(groupsRequestBuilder.byGroupId("group-id")).thenReturn(groupRequestBuilder);
        when(groupRequestBuilder.transitiveMembers()).thenReturn(membersRequestBuilder);
        when(membersRequestBuilder.graphUser()).thenReturn(graphUserRequestBuilder);

        ArgumentCaptor<Consumer<GraphUserRequestBuilder.GetRequestConfiguration>> reqConfigCaptor =
                ArgumentCaptor.captor();
        when(graphUserRequestBuilder.get(reqConfigCaptor.capture())).thenReturn(mockResponse);

        AzureUsersFetcher fetcher = new AzureUsersFetcher(mockGraphClient);

        // When
        List<PlatformUsersFetcher.Membership> memberships = fetcher.fetchMembershipsByGroupRef("group-id");

        // Then
        assertEquals(2, memberships.size());
        assertEquals("user1@example.com", memberships.get(0).email());
        assertEquals("user2@example.com", memberships.get(1).email());

        var requestConfigurationMock = mock(GraphUserRequestBuilder.GetRequestConfiguration.class);
        requestConfigurationMock.queryParameters = mock(GraphUserRequestBuilder.GetQueryParameters.class);
        reqConfigCaptor.getValue().accept(requestConfigurationMock);
        assertEquals(4, requestConfigurationMock.queryParameters.select.length);
        assertArrayEquals(
                new String[] {"mail", "accountEnabled", "deletedDateTime", "userPrincipalName"},
                requestConfigurationMock.queryParameters.select);
    }
}
