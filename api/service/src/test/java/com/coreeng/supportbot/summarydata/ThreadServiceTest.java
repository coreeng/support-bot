package com.coreeng.supportbot.summarydata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.coreeng.supportbot.config.SummaryDataProps;
import com.coreeng.supportbot.config.SummaryDataProps.SanitisationProperties;
import com.coreeng.supportbot.slack.SlackException;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.response.conversations.ConversationsRepliesResponse;
import com.slack.api.model.Message;
import com.slack.api.model.ResponseMetadata;
import java.util.List;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ThreadServiceTest {

    private static final String CHANNEL_ID = "C123456";
    private static final String THREAD_TS = "1234567890.123456";

    @Mock
    private SlackClient slackClient;

    @Test
    void getThreadAsText_noPatternsConfigured_textUnchanged() {
        // Given — no sanitisation patterns configured
        var service = serviceWithSanitisation(List.of(), List.of());
        var response = singlePageResponse(List.of(messageWithText("Hello <@U12345678> john@example.com")));
        when(slackClient.getThreadPage(any())).thenReturn(response);

        // When
        String result = service.getThreadAsText(CHANNEL_ID, THREAD_TS);

        // Then — text passes through unchanged
        assertThat(result).isEqualTo("Hello <@U12345678> john@example.com");
    }

    @Test
    void getThreadAsText_slackMentionPattern_removesMentions() {
        // Given — Slack mention pattern configured
        var service = serviceWithSanitisation(List.of("<?@?[UW][A-Z0-9]{8,}>?"), List.of());
        var response = singlePageResponse(List.of(messageWithText("Hello <@U12345678> can you help?")));
        when(slackClient.getThreadPage(any())).thenReturn(response);

        // When
        String result = service.getThreadAsText(CHANNEL_ID, THREAD_TS);

        // Then — the mention <@U12345678> should be stripped
        assertThat(result).doesNotContain("<@U12345678>");
        assertThat(result).doesNotContain("U12345678");
        assertThat(result).contains("can you help?");
    }

    @Test
    void getThreadAsText_emailPattern_removesEmails() {
        // Given — email pattern configured
        var service = serviceWithSanitisation(List.of("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"), List.of());
        var response = singlePageResponse(List.of(messageWithText("Contact john@example.com for help")));
        when(slackClient.getThreadPage(any())).thenReturn(response);

        // When
        String result = service.getThreadAsText(CHANNEL_ID, THREAD_TS);

        // Then — email removed, surrounding text preserved
        assertThat(result).doesNotContain("john@example.com");
        assertThat(result).contains("Contact");
        assertThat(result).contains("for help");
    }

    @Test
    void getThreadAsText_mailtoPattern_removesMailtoLinks() {
        // Given — Slack mailto link pattern configured
        var service = serviceWithSanitisation(List.of("<mailto:[^|>]+\\|[^>]+>"), List.of());
        var response = singlePageResponse(
                List.of(messageWithText("Email <mailto:john@example.com|john@example.com> for info")));
        when(slackClient.getThreadPage(any())).thenReturn(response);

        // When
        String result = service.getThreadAsText(CHANNEL_ID, THREAD_TS);

        // Then — mailto link removed entirely
        assertThat(result).doesNotContain("mailto");
        assertThat(result).doesNotContain("john@example.com");
        assertThat(result).contains("Email");
        assertThat(result).contains("for info");
    }

    @Test
    void getThreadAsText_exceptionsPreserved() {
        // Given — pattern matches capitalised words, but "Monday" is an exception
        var service = serviceWithSanitisation(List.of("[A-Z][a-z]+"), List.of("Monday"));
        var response = singlePageResponse(List.of(messageWithText("Monday meeting with Oleg")));
        when(slackClient.getThreadPage(any())).thenReturn(response);

        // When
        String result = service.getThreadAsText(CHANNEL_ID, THREAD_TS);

        // Then — "Monday" kept, "Oleg" removed
        assertThat(result).contains("Monday");
        assertThat(result).doesNotContain("Oleg");
    }

    @Test
    void getThreadAsText_multiplePatternsAppliedInOrder() {
        // Given — both mention and email patterns configured
        var service = serviceWithSanitisation(
                List.of("<?@?[UW][A-Z0-9]{8,}>?", "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"), List.of());
        var response = singlePageResponse(List.of(messageWithText("<@U12345678> contact john@example.com")));
        when(slackClient.getThreadPage(any())).thenReturn(response);

        // When
        String result = service.getThreadAsText(CHANNEL_ID, THREAD_TS);

        // Then — both mention and email removed
        assertThat(result).doesNotContain("U12345678");
        assertThat(result).doesNotContain("john@example.com");
        assertThat(result).contains("contact");
    }

    @Test
    void getThreadAsText_shouldJoinMessagesWithDoubleNewlines() {
        // Given — two messages
        var service = serviceWithSanitisation(List.of(), List.of());
        var response = singlePageResponse(List.of(messageWithText("first message"), messageWithText("second message")));
        when(slackClient.getThreadPage(any())).thenReturn(response);

        // When
        String result = service.getThreadAsText(CHANNEL_ID, THREAD_TS);

        // Then — joined with double newline
        assertThat(result).isEqualTo("first message\n\nsecond message");
    }

    @Test
    void getThreadAsText_shouldFilterEmptyMessages() {
        // Given — mix of valid, empty, and null text messages
        var service = serviceWithSanitisation(List.of(), List.of());
        var emptyMsg = messageWithText("");
        var nullMsg = new Message();
        var validMsg = messageWithText("valid message");
        var response = singlePageResponse(List.of(emptyMsg, nullMsg, validMsg));
        when(slackClient.getThreadPage(any())).thenReturn(response);

        // When
        String result = service.getThreadAsText(CHANNEL_ID, THREAD_TS);

        // Then — only the valid message remains
        assertThat(result).isEqualTo("valid message");
    }

    @Test
    void getAllThreadMessageTexts_shouldHandleMultiplePages() {
        // Given — first page has more, second page is final
        var service = serviceWithSanitisation(List.of(), List.of());

        var page1 = new ConversationsRepliesResponse();
        page1.setOk(true);
        page1.setMessages(List.of(messageWithText("page1 msg1"), messageWithText("page1 msg2")));
        page1.setHasMore(true);
        var metadata = new ResponseMetadata();
        metadata.setNextCursor("cursor_abc");
        page1.setResponseMetadata(metadata);

        var page2 = new ConversationsRepliesResponse();
        page2.setOk(true);
        page2.setMessages(List.of(messageWithText("page2 msg1")));
        page2.setHasMore(false);

        when(slackClient.getThreadPage(any())).thenReturn(page1).thenReturn(page2);

        // When
        var result = service.getAllThreadMessageTexts(CHANNEL_ID, THREAD_TS);

        // Then — messages from both pages combined
        assertThat(result).containsExactly("page1 msg1", "page1 msg2", "page2 msg1");
    }

    @Test
    void constructor_invalidRegexPattern_throwsWithPatternDetails() {
        // Given — an invalid regex pattern
        var invalidPattern = "[unclosed";

        // When
        var thrown = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> serviceWithSanitisation(List.of(invalidPattern), List.of()));

        // Then — message identifies the bad pattern
        assertThat(thrown.getMessage()).contains("Invalid sanitisation pattern");
        assertThat(thrown.getMessage()).contains("[unclosed");
        assertThat(thrown).hasCauseInstanceOf(java.util.regex.PatternSyntaxException.class);
    }

    @Test
    void getThreadAsText_exceptionMatchingIsCaseInsensitive() {
        // Given — "Monday" is an exception, should match regardless of case
        var service = serviceWithSanitisation(List.of("[A-Za-z]+day"), List.of("Monday"));
        var response = singlePageResponse(List.of(messageWithText("Monday monday Friday")));
        when(slackClient.getThreadPage(any())).thenReturn(response);

        // When
        String result = service.getThreadAsText(CHANNEL_ID, THREAD_TS);

        // Then — both "Monday" and "monday" preserved, "Friday" removed
        assertThat(result).contains("Monday");
        assertThat(result).contains("monday");
        assertThat(result).doesNotContain("Friday");
    }

    @Test
    void getThreadAsText_realWorldContent_emailsAndMentionsRemoved() {
        // Given — all three sanitisation patterns configured (mentions, mailto, emails)
        var service = serviceWithSanitisation(
                List.of(
                        "<?@?[UW][A-Z0-9]{8,}>?",
                        "<mailto:[^|>]+\\|[^>]+>",
                        "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"),
                List.of());

        var msg1 = messageWithText("Please grant access for <mailto:jane.doe@example.com|jane.doe@example.com>");
        var msg2 = messageWithText("""
                config:
                  owner-email: <mailto:john.smith@example.com|john.smith@example.com>
                  group: team-alpha-nonprod""");
        var msg3 = messageWithText("contact: ops-team@example.com for help");

        var response = singlePageResponse(List.of(msg1, msg2, msg3));
        when(slackClient.getThreadPage(any())).thenReturn(response);

        // When
        String result = service.getThreadAsText(CHANNEL_ID, THREAD_TS);

        // Then — all emails and mailto links removed, non-PII content preserved
        assertThat(result).doesNotContain("jane.doe@example.com");
        assertThat(result).doesNotContain("john.smith@example.com");
        assertThat(result).doesNotContain("ops-team@example.com");
        assertThat(result).doesNotContain("mailto:");
        assertThat(result).contains("Please grant access for");
        assertThat(result).contains("group: team-alpha-nonprod");
        assertThat(result).contains("contact:");
    }

    @Test
    void getAllThreadMessageTexts_shouldRetryOnceOnRateLimit() {
        // Given
        var service = serviceWithSanitisation(List.of(), List.of());
        when(slackClient.getThreadPage(any()))
                .thenThrow(rateLimitedSlackException("1"))
                .thenReturn(singlePageResponse(List.of(messageWithText("recovered"))));

        // When
        var result = service.getAllThreadMessageTexts(CHANNEL_ID, THREAD_TS);

        // Then
        assertThat(result).containsExactly("recovered");
        verify(slackClient, times(2)).getThreadPage(any());
    }

    @Test
    void getAllThreadMessageTexts_shouldRethrowNonRateLimitSlackException() {
        // Given
        var service = serviceWithSanitisation(List.of(), List.of());
        var exception = new SlackException(new RuntimeException("channel_not_found"));
        when(slackClient.getThreadPage(any())).thenThrow(exception);

        // When / Then
        assertThatThrownBy(() -> service.getAllThreadMessageTexts(CHANNEL_ID, THREAD_TS))
                .isSameAs(exception);
    }

    @Test
    void getAllThreadMessageTexts_shouldPropagateWhenRetryAlsoFails() {
        // Given
        var service = serviceWithSanitisation(List.of(), List.of());
        when(slackClient.getThreadPage(any())).thenThrow(rateLimitedSlackException("1"));

        // When / Then
        assertThatThrownBy(() -> service.getAllThreadMessageTexts(CHANNEL_ID, THREAD_TS))
                .isInstanceOf(SlackException.class);
        verify(slackClient, times(2)).getThreadPage(any());
    }

    private ThreadService serviceWithSanitisation(List<String> patterns, List<String> exceptions) {
        var sanitisation = new SanitisationProperties(patterns, exceptions);
        var props = new SummaryDataProps("classpath:placeholder-analysis-bundle.zip", sanitisation);
        return new ThreadService(slackClient, props);
    }

    private ConversationsRepliesResponse singlePageResponse(List<Message> messages) {
        var response = new ConversationsRepliesResponse();
        response.setOk(true);
        response.setMessages(messages);
        response.setHasMore(false);
        return response;
    }

    private Message messageWithText(String text) {
        var msg = new Message();
        msg.setText(text);
        return msg;
    }

    private static SlackException rateLimitedSlackException(String retryAfterSeconds) {
        Response okHttpResponse = new Response.Builder()
                .request(new Request.Builder().url("https://slack.com/api/test").build())
                .protocol(Protocol.HTTP_1_1)
                .code(429)
                .message("Too Many Requests")
                .header("Retry-After", retryAfterSeconds)
                .build();
        SlackApiException cause = new SlackApiException(okHttpResponse, "{\"ok\":false,\"error\":\"ratelimited\"}");
        return new SlackException(cause);
    }
}
