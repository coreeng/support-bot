package com.coreeng.supportbot.teams;

import com.coreeng.supportbot.slack.client.SlackClient;
import com.google.common.collect.ImmutableList;
import com.slack.api.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class GenericSlackMemberFetcherTest {

    private SlackClient slackClient;
    private ExecutorService executor;
    private GenericSlackMemberFetcher fetcher;

    @BeforeEach
    void setUp() {
        slackClient = mock(SlackClient.class);
        executor = Executors.newVirtualThreadPerTaskExecutor();
        fetcher = new GenericSlackMemberFetcher(slackClient, executor);
    }

    private User.Profile createProfile(String email) {
        User.Profile profile = new User.Profile();
        profile.setEmail(email);
        return profile;
    }

    @Test
    void loadInitialMembersReturnsEmptyListForEmptyGroup() {
        // Given
        when(slackClient.getGroupMembers("GROUP_ID")).thenReturn(ImmutableList.of());

        // When
        ImmutableList<TeamMemberFetcher.TeamMember> result = fetcher.loadInitialMembers("GROUP_ID");

        // Then
        assertThat(result).isEmpty();
        verify(slackClient).getGroupMembers("GROUP_ID");
    }

    @Test
    void loadInitialMembersFetchesUserDetailsFromSlack() {
        // Given
        when(slackClient.getGroupMembers("GROUP_ID")).thenReturn(ImmutableList.of("U1", "U2"));
        when(slackClient.getUserById("U1")).thenReturn(createProfile("user1@example.com"));
        when(slackClient.getUserById("U2")).thenReturn(createProfile("user2@example.com"));

        // When
        ImmutableList<TeamMemberFetcher.TeamMember> result = fetcher.loadInitialMembers("GROUP_ID");

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).extracting(TeamMemberFetcher.TeamMember::email)
                .containsExactlyInAnyOrder("user1@example.com", "user2@example.com");
        assertThat(result).extracting(TeamMemberFetcher.TeamMember::slackId)
                .containsExactlyInAnyOrder("U1", "U2");
        verify(slackClient).getGroupMembers("GROUP_ID");
        verify(slackClient).getUserById("U1");
        verify(slackClient).getUserById("U2");
    }

    @Test
    void loadInitialMembersHandlesMultipleUsers() {
        // Given
        ImmutableList<String> userIds = ImmutableList.of("U1", "U2", "U3", "U4", "U5");
        when(slackClient.getGroupMembers("LARGE_GROUP")).thenReturn(userIds);
        
        for (String userId : userIds) {
            when(slackClient.getUserById(userId))
                    .thenReturn(createProfile(userId.toLowerCase() + "@example.com"));
        }

        // When
        ImmutableList<TeamMemberFetcher.TeamMember> result = fetcher.loadInitialMembers("LARGE_GROUP");

        // Then
        assertThat(result).hasSize(5);
        for (String userId : userIds) {
            verify(slackClient).getUserById(userId);
        }
    }

    @Test
    void handleMembershipUpdateReturnsMembersFromUserIds() {
        // Given
        ImmutableList<String> userIds = ImmutableList.of("U10", "U20");
        when(slackClient.getUserById("U10")).thenReturn(createProfile("updated1@example.com"));
        when(slackClient.getUserById("U20")).thenReturn(createProfile("updated2@example.com"));

        // When
        ImmutableList<TeamMemberFetcher.TeamMember> result = 
                fetcher.handleMembershipUpdate("GROUP_ID", userIds);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).extracting(TeamMemberFetcher.TeamMember::email)
                .containsExactlyInAnyOrder("updated1@example.com", "updated2@example.com");
        assertThat(result).extracting(TeamMemberFetcher.TeamMember::slackId)
                .containsExactlyInAnyOrder("U10", "U20");
        verify(slackClient, never()).getGroupMembers(anyString());
        verify(slackClient).getUserById("U10");
        verify(slackClient).getUserById("U20");
    }

    @Test
    void handleMembershipUpdateReturnsEmptyListForNoUsers() {
        // When
        ImmutableList<TeamMemberFetcher.TeamMember> result = 
                fetcher.handleMembershipUpdate("GROUP_ID", ImmutableList.of());

        // Then
        assertThat(result).isEmpty();
        verify(slackClient, never()).getGroupMembers(anyString());
        verify(slackClient, never()).getUserById(anyString());
    }

    @Test
    void handleMembershipUpdateDoesNotCallGetGroupMembers() {
        // Given
        ImmutableList<String> userIds = ImmutableList.of("U99");
        when(slackClient.getUserById("U99")).thenReturn(createProfile("user@example.com"));

        // When
        fetcher.handleMembershipUpdate("IGNORED_GROUP_ID", userIds);

        // Then
        verify(slackClient, never()).getGroupMembers(anyString());
        verify(slackClient).getUserById("U99");
    }
}

