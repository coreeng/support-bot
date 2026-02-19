package com.coreeng.supportbot.summarydata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.coreeng.supportbot.slack.client.SlackClient;
import com.slack.api.methods.response.conversations.ConversationsRepliesResponse;
import com.slack.api.model.Message;
import com.slack.api.model.ResponseMetadata;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
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

    private ThreadService threadService;

    @BeforeEach
    public void setUp() {
        threadService = new ThreadService(slackClient);
    }

    @Test
    void getThreadAsText_shouldRemoveSlackMentions() {
        // Given
        var response = singlePageResponse(List.of(messageWithText("Hello <@U12345678> can you help?")));
        when(slackClient.getThreadPage(any())).thenReturn(response);

        // When
        String result = threadService.getThreadAsText(CHANNEL_ID, THREAD_TS);

        // Then — the mention <@U12345678> should be stripped
        assertThat(result).doesNotContain("<@U12345678>");
        assertThat(result).doesNotContain("U12345678");
        assertThat(result).contains("can you help?");
    }

    @Test
    void getThreadAsText_shouldRemoveLikelyNames() {
        // Given — "Oleg" is not a common word so it should be removed
        var response = singlePageResponse(List.of(messageWithText("Thanks Oleg for helping")));
        when(slackClient.getThreadPage(any())).thenReturn(response);

        // When
        String result = threadService.getThreadAsText(CHANNEL_ID, THREAD_TS);

        // Then — "Oleg" removed; "Thanks" may also be removed as part of multi-word match
        assertThat(result).doesNotContain("Oleg");
        assertThat(result).contains("for helping");
    }

    @Test
    void getThreadAsText_shouldRemoveMultiWordNames() {
        // Given — "John Smith" is a multi-word capitalized phrase with no common words
        var response = singlePageResponse(List.of(messageWithText("John Smith fixed the pipeline")));
        when(slackClient.getThreadPage(any())).thenReturn(response);

        // When
        String result = threadService.getThreadAsText(CHANNEL_ID, THREAD_TS);

        // Then — both name words removed
        assertThat(result).doesNotContain("John");
        assertThat(result).doesNotContain("Smith");
        assertThat(result).contains("fixed the pipeline");
    }

    @Test
    void getThreadAsText_shouldPreserveCommonCapitalizedWords() {
        // Given — "Monday" and "January" are in commonly-capitalised-words.txt
        var response = singlePageResponse(List.of(messageWithText("Monday meeting about the January release")));
        when(slackClient.getThreadPage(any())).thenReturn(response);

        // When
        String result = threadService.getThreadAsText(CHANNEL_ID, THREAD_TS);

        // Then — common words preserved
        assertThat(result).contains("Monday");
        assertThat(result).contains("January");
    }

    @Test
    void getThreadAsText_shouldJoinMessagesWithDoubleNewlines() {
        // Given — two messages
        var response = singlePageResponse(List.of(messageWithText("first message"), messageWithText("second message")));
        when(slackClient.getThreadPage(any())).thenReturn(response);

        // When
        String result = threadService.getThreadAsText(CHANNEL_ID, THREAD_TS);

        // Then — joined with double newline
        assertThat(result).isEqualTo("first message\n\nsecond message");
    }

    @Test
    void getThreadAsText_shouldFilterEmptyMessages() {
        // Given — mix of valid, empty, and null text messages
        var emptyMsg = messageWithText("");
        var nullMsg = new Message(); // text defaults to null
        var validMsg = messageWithText("valid message");
        var response = singlePageResponse(List.of(emptyMsg, nullMsg, validMsg));
        when(slackClient.getThreadPage(any())).thenReturn(response);

        // When
        String result = threadService.getThreadAsText(CHANNEL_ID, THREAD_TS);

        // Then — only the valid message remains
        assertThat(result).isEqualTo("valid message");
    }

    @Test
    void getAllThreadMessageTexts_shouldHandleMultiplePages() {
        // Given — first page has more, second page is final
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
        var result = threadService.getAllThreadMessageTexts(CHANNEL_ID, THREAD_TS);

        // Then — messages from both pages combined
        assertThat(result).containsExactly("page1 msg1", "page1 msg2", "page2 msg1");
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
}
