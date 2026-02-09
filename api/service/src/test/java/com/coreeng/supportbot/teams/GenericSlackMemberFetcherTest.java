package com.coreeng.supportbot.teams;

import com.coreeng.supportbot.slack.SlackId;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.google.common.collect.ImmutableList;
import com.slack.api.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Locale;
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

    private User createUser(String email) {
        User.Profile profile = new User.Profile();
        profile.setEmail(email);
        User user = new User();
        user.setProfile(profile);
        return user;
    }

    @Test
    void loadInitialMembersReturnsEmptyListForEmptyGroup() {
        // Given
        when(slackClient.getGroupMembers(SlackId.group("GROUP_ID"))).thenReturn(ImmutableList.of());

        // When
        ImmutableList<TeamMemberFetcher.TeamMember> result = fetcher.loadInitialMembers(SlackId.group("GROUP_ID"));

        // Then
        assertThat(result).isEmpty();
        verify(slackClient).getGroupMembers(SlackId.group("GROUP_ID"));
    }

    @Test
    void loadInitialMembersFetchesUserDetailsFromSlack() {
        // Given
        when(slackClient.getGroupMembers(SlackId.group("GROUP_ID"))).thenReturn(ImmutableList.of("U1", "U2"));
        when(slackClient.getUserById(SlackId.user("U1"))).thenReturn(createUser("user1@example.com"));
        when(slackClient.getUserById(SlackId.user("U2"))).thenReturn(createUser("user2@example.com"));

        // When
        ImmutableList<TeamMemberFetcher.TeamMember> result = fetcher.loadInitialMembers(SlackId.group("GROUP_ID"));

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).extracting(TeamMemberFetcher.TeamMember::email)
                .containsExactlyInAnyOrder("user1@example.com", "user2@example.com");
        assertThat(result).extracting(TeamMemberFetcher.TeamMember::slackId)
                .containsExactlyInAnyOrder(SlackId.user("U1"), SlackId.user("U2"));
        verify(slackClient).getGroupMembers(SlackId.group("GROUP_ID"));
        verify(slackClient).getUserById(SlackId.user("U1"));
        verify(slackClient).getUserById(SlackId.user("U2"));
    }

    @Test
    void loadInitialMembersHandlesMultipleUsers() {
        // Given
        ImmutableList<String> userIdStrings = ImmutableList.of("U1", "U2", "U3", "U4", "U5");
        ImmutableList<SlackId.User> userIds = userIdStrings.stream()
                .map(SlackId::user)
                .collect(ImmutableList.toImmutableList());
        when(slackClient.getGroupMembers(SlackId.group("LARGE_GROUP"))).thenReturn(userIdStrings);

        for (SlackId.User userId : userIds) {
            when(slackClient.getUserById(userId))
                    .thenReturn(createUser(userId.id().toLowerCase(Locale.ROOT) + "@example.com"));
        }

        // When
        ImmutableList<TeamMemberFetcher.TeamMember> result = fetcher.loadInitialMembers(SlackId.group("LARGE_GROUP"));

        // Then
        assertThat(result).hasSize(5);
        for (SlackId.User userId : userIds) {
            verify(slackClient).getUserById(userId);
        }
    }

    @Test
    void handleMembershipUpdateReturnsMembersFromUserIds() {
        // Given
        ImmutableList<SlackId.User> userIds = ImmutableList.of(SlackId.user("U10"), SlackId.user("U20"));
        when(slackClient.getUserById(SlackId.user("U10"))).thenReturn(createUser("updated1@example.com"));
        when(slackClient.getUserById(SlackId.user("U20"))).thenReturn(createUser("updated2@example.com"));

        // When
        ImmutableList<TeamMemberFetcher.TeamMember> result =
                fetcher.handleMembershipUpdate(SlackId.group("GROUP_ID"), userIds);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).extracting(TeamMemberFetcher.TeamMember::email)
                .containsExactlyInAnyOrder("updated1@example.com", "updated2@example.com");
        assertThat(result).extracting(TeamMemberFetcher.TeamMember::slackId)
                .containsExactlyInAnyOrder(SlackId.user("U10"), SlackId.user("U20"));
        verify(slackClient, never()).getGroupMembers(any(SlackId.Group.class));
        verify(slackClient).getUserById(SlackId.user("U10"));
        verify(slackClient).getUserById(SlackId.user("U20"));
    }

    @Test
    void handleMembershipUpdateReturnsEmptyListForNoUsers() {
        // When
        ImmutableList<TeamMemberFetcher.TeamMember> result =
                fetcher.handleMembershipUpdate(SlackId.group("GROUP_ID"), ImmutableList.of());

        // Then
        assertThat(result).isEmpty();
        verify(slackClient, never()).getGroupMembers(any(SlackId.Group.class));
        verify(slackClient, never()).getUserById(any(SlackId.User.class));
    }

    @Test
    void handleMembershipUpdateDoesNotCallGetGroupMembers() {
        // Given
        ImmutableList<SlackId.User> userIds = ImmutableList.of(SlackId.user("U99"));
        when(slackClient.getUserById(SlackId.user("U99"))).thenReturn(createUser("user@example.com"));

        // When
        fetcher.handleMembershipUpdate(SlackId.group("IGNORED_GROUP_ID"), userIds);

        // Then
        verify(slackClient, never()).getGroupMembers(any(SlackId.Group.class));
        verify(slackClient).getUserById(SlackId.user("U99"));
    }
}
