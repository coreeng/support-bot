package com.coreeng.supportbot.slack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.coreeng.supportbot.slack.client.SlackClient;
import com.slack.api.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SlackTextFormatterTest {

    private SlackClient slackClient;
    private SlackTextFormatter formatter;

    @BeforeEach
    void setUp() {
        slackClient = mock(SlackClient.class);
        formatter = new SlackTextFormatter(slackClient);
    }

    @Test
    void formatReturnsNullForNullInput() {
        assertThat(formatter.format(null)).isNull();
    }

    @Test
    void formatReturnsEmptyStringForEmptyInput() {
        assertThat(formatter.format("")).isEqualTo("");
    }

    @Test
    void formatReturnsBlankStringForBlankInput() {
        assertThat(formatter.format("   ")).isEqualTo("   ");
    }

    @Test
    void formatReturnsPlainTextUnchanged() {
        String text = "This is plain text with no mentions";
        assertThat(formatter.format(text)).isEqualTo(text);
    }

    @Test
    void formatUserMentionWithSuccessfulLookup() {
        // Given
        User user = createUser("U123456", "John Doe");
        when(slackClient.getUserById(SlackId.user("U123456"))).thenReturn(user);

        // When
        String result = formatter.format("Hello <@U123456>!");

        // Then
        assertThat(result).isEqualTo("Hello @John Doe!");
        verify(slackClient).getUserById(SlackId.user("U123456"));
    }

    @Test
    void formatUserMentionWithFailedLookup() {
        // Given
        when(slackClient.getUserById(SlackId.user("U123456"))).thenThrow(new RuntimeException("User not found"));

        // When
        String result = formatter.format("Hello <@U123456>!");

        // Then
        assertThat(result).isEqualTo("Hello @U123456!");
        verify(slackClient).getUserById(SlackId.user("U123456"));
    }

    @Test
    void formatUserMentionWithWPrefix() {
        // Given
        User user = createUser("W123456", "Jane Smith");
        when(slackClient.getUserById(SlackId.user("W123456"))).thenReturn(user);

        // When
        String result = formatter.format("Hi <@W123456>");

        // Then
        assertThat(result).isEqualTo("Hi @Jane Smith");
    }

    @Test
    void formatSubteamMentionWithHandle() {
        // When
        String result = formatter.format("Hello <!subteam^S08948NBMED|@core-support>!");

        // Then
        assertThat(result).isEqualTo("Hello @core-support!");
        verify(slackClient, never()).getGroupName(any(SlackId.Group.class));
    }

    @Test
    void formatSubteamMentionWithoutHandleWithSuccessfulLookup() {
        // Given
        when(slackClient.getGroupName(SlackId.group("S08948NBMED"))).thenReturn("core-support");

        // When
        String result = formatter.format("Hello <!subteam^S08948NBMED>!");

        // Then
        assertThat(result).isEqualTo("Hello @core-support!");
        verify(slackClient).getGroupName(SlackId.group("S08948NBMED"));
    }

    @Test
    void formatSubteamMentionWithoutHandleWithFailedLookup() {
        // Given
        when(slackClient.getGroupName(SlackId.group("S08948NBMED"))).thenReturn(null);

        // When
        String result = formatter.format("Hello <!subteam^S08948NBMED>!");

        // Then
        assertThat(result).isEqualTo("Hello @group-S08948NBMED!");
        verify(slackClient).getGroupName(SlackId.group("S08948NBMED"));
    }

    @Test
    void formatSubteamMentionWithException() {
        // Given
        when(slackClient.getGroupName(SlackId.group("S08948NBMED"))).thenThrow(new RuntimeException("API error"));

        // When
        String result = formatter.format("Hello <!subteam^S08948NBMED>!");

        // Then
        assertThat(result).isEqualTo("Hello @group-S08948NBMED!");
        verify(slackClient).getGroupName(SlackId.group("S08948NBMED"));
    }

    @Test
    void formatChannelMentionWithName() {
        // When
        String result = formatter.format("Check <#C08E1PXF9U4|general> channel");

        // Then
        assertThat(result).isEqualTo("Check #general channel");
        verify(slackClient, never()).getChannelName(anyString());
    }

    @Test
    void formatChannelMentionWithEmptyName() {
        // Given
        when(slackClient.getChannelName("C08E1PXF9U4")).thenReturn("general");

        // When
        String result = formatter.format("Check <#C08E1PXF9U4|> channel");

        // Then
        assertThat(result).isEqualTo("Check #general channel");
        verify(slackClient).getChannelName("C08E1PXF9U4");
    }

    @Test
    void formatChannelMentionWithoutNameWithSuccessfulLookup() {
        // Given
        when(slackClient.getChannelName("C08E1PXF9U4")).thenReturn("general");

        // When
        String result = formatter.format("Check <#C08E1PXF9U4> channel");

        // Then
        assertThat(result).isEqualTo("Check #general channel");
        verify(slackClient).getChannelName("C08E1PXF9U4");
    }

    @Test
    void formatChannelMentionWithoutNameWithFailedLookup() {
        // Given
        when(slackClient.getChannelName("C08E1PXF9U4")).thenReturn(null);

        // When
        String result = formatter.format("Check <#C08E1PXF9U4> channel");

        // Then
        assertThat(result).isEqualTo("Check #C08E1PXF9U4 channel");
        verify(slackClient).getChannelName("C08E1PXF9U4");
    }

    @Test
    void formatChannelMentionWithException() {
        // Given
        when(slackClient.getChannelName("C08E1PXF9U4")).thenThrow(new RuntimeException("API error"));

        // When
        String result = formatter.format("Check <#C08E1PXF9U4> channel");

        // Then
        assertThat(result).isEqualTo("Check #C08E1PXF9U4 channel");
        verify(slackClient).getChannelName("C08E1PXF9U4");
    }

    @Test
    void formatLinkWithText() {
        // When
        String result = formatter.format("Visit <https://example.com|Example>");

        // Then
        assertThat(result).isEqualTo("Visit Example (https://example.com)");
    }

    @Test
    void formatLinkWithoutText() {
        // When
        String result = formatter.format("Visit <https://example.com>");

        // Then
        assertThat(result).isEqualTo("Visit https://example.com");
    }

    @Test
    void formatSpecialMentionHere() {
        // When
        String result = formatter.format("Attention <!here>!");

        // Then
        assertThat(result).isEqualTo("Attention @here!");
    }

    @Test
    void formatSpecialMentionChannel() {
        // When
        String result = formatter.format("Attention <!channel>!");

        // Then
        assertThat(result).isEqualTo("Attention @channel!");
    }

    @Test
    void formatSpecialMentionEveryone() {
        // When
        String result = formatter.format("Attention <!everyone>!");

        // Then
        assertThat(result).isEqualTo("Attention @everyone!");
    }

    @Test
    void formatSpecialMentionOther() {
        // When
        String result = formatter.format("Attention <!custom>!");

        // Then
        assertThat(result).isEqualTo("Attention <!custom>!");
    }

    @Test
    void formatMultipleMentions() {
        // Given
        User user1 = createUser("U123", "Alice");
        User user2 = createUser("U456", "Bob");
        when(slackClient.getUserById(SlackId.user("U123"))).thenReturn(user1);
        when(slackClient.getUserById(SlackId.user("U456"))).thenReturn(user2);

        // When
        String result = formatter.format("Hello <@U123> and <@U456>!");

        // Then
        assertThat(result).isEqualTo("Hello @Alice and @Bob!");
        verify(slackClient).getUserById(SlackId.user("U123"));
        verify(slackClient).getUserById(SlackId.user("U456"));
    }

    @Test
    void formatMixedContent() {
        // Given
        User user = createUser("U123", "Alice");
        when(slackClient.getUserById(SlackId.user("U123"))).thenReturn(user);
        when(slackClient.getChannelName("C08E1PXF9U4")).thenReturn("general");

        // When
        String result =
                formatter.format("Hello <@U123>! Check <#C08E1PXF9U4|general> and visit <https://example.com|Example>");

        // Then
        assertThat(result).isEqualTo("Hello @Alice! Check #general and visit Example (https://example.com)");
    }

    @Test
    void formatComplexMessage() {
        // Given
        User user = createUser("U123", "Alice");
        when(slackClient.getUserById(SlackId.user("U123"))).thenReturn(user);
        when(slackClient.getGroupName(SlackId.group("S08948NBMED"))).thenReturn("core-support");
        when(slackClient.getChannelName("C08E1PXF9U4")).thenReturn("general");

        // When
        String result = formatter.format(
                "Hello <@U123> and <!subteam^S08948NBMED>! Check <#C08E1PXF9U4> and <!here>! Visit <https://example.com|link>");

        // Then
        assertThat(result)
                .isEqualTo(
                        "Hello @Alice and @core-support! Check #general and @here! Visit link (https://example.com)");
    }

    @Test
    void formatPreservesNonMentionContent() {
        // When
        String result = formatter.format("This is a normal message with no special formatting.");

        // Then
        assertThat(result).isEqualTo("This is a normal message with no special formatting.");
        verify(slackClient, never()).getUserById(any(SlackId.User.class));
        verify(slackClient, never()).getGroupName(any(SlackId.Group.class));
        verify(slackClient, never()).getChannelName(anyString());
    }

    @Test
    void formatHandlesMultipleLinks() {
        // When
        String result = formatter.format("Link1: <https://example.com|Example> and Link2: <https://test.com>");

        // Then
        assertThat(result).isEqualTo("Link1: Example (https://example.com) and Link2: https://test.com");
    }

    private User createUser(String userId, String name) {
        User.Profile profile = new User.Profile();
        profile.setDisplayName(name);
        User user = new User();
        user.setId(userId);
        user.setName(name);
        user.setProfile(profile);
        return user;
    }
}
