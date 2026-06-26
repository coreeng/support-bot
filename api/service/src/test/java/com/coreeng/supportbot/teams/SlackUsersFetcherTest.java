package com.coreeng.supportbot.teams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.coreeng.supportbot.slack.SlackId;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.coreeng.supportbot.teams.PlatformUsersFetcher.Membership;
import com.coreeng.supportbot.teams.groups.GroupRef;
import com.google.common.collect.ImmutableList;
import com.slack.api.model.User;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SlackUsersFetcherTest {

    private SlackClient slackClient;
    private ExecutorService executor;
    private SlackUsersFetcher fetcher;

    @BeforeEach
    void setUp() {
        slackClient = mock(SlackClient.class);
        executor = Executors.newVirtualThreadPerTaskExecutor();
        fetcher = new SlackUsersFetcher(slackClient, executor);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    private User userWithEmail(@Nullable String email) {
        User.Profile profile = new User.Profile();
        profile.setEmail(email);
        User user = new User();
        user.setProfile(profile);
        return user;
    }

    @Test
    void resolvesUsergroupMembersToEmails() {
        when(slackClient.getGroupMembers(SlackId.group("S08948NBMED"))).thenReturn(ImmutableList.of("U1", "U2"));
        when(slackClient.getUserById(SlackId.user("U1"))).thenReturn(userWithEmail("a@example.com"));
        when(slackClient.getUserById(SlackId.user("U2"))).thenReturn(userWithEmail("b@example.com"));

        List<Membership> result = fetcher.fetchMembershipsByGroupRef(new GroupRef.Slack("S08948NBMED"));

        assertThat(result).extracting(Membership::email).containsExactlyInAnyOrder("a@example.com", "b@example.com");
    }

    @Test
    void returnsEmptyForEmptyUsergroup() {
        when(slackClient.getGroupMembers(SlackId.group("EMPTY"))).thenReturn(ImmutableList.of());

        assertThat(fetcher.fetchMembershipsByGroupRef(new GroupRef.Slack("EMPTY")))
                .isEmpty();
        verify(slackClient, never()).getUserById(any());
    }

    @Test
    void skipsMembersWithoutAnEmail() {
        when(slackClient.getGroupMembers(SlackId.group("S1"))).thenReturn(ImmutableList.of("U1", "U2", "U3"));
        when(slackClient.getUserById(SlackId.user("U1"))).thenReturn(userWithEmail("a@example.com"));
        when(slackClient.getUserById(SlackId.user("U2"))).thenReturn(userWithEmail(null)); // no email on profile
        when(slackClient.getUserById(SlackId.user("U3"))).thenReturn(userWithEmail("   ")); // blank email

        List<Membership> result = fetcher.fetchMembershipsByGroupRef(new GroupRef.Slack("S1"));

        assertThat(result).extracting(Membership::email).containsExactly("a@example.com");
    }
}
