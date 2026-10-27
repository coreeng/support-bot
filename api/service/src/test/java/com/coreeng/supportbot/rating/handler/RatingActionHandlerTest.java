package com.coreeng.supportbot.rating.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.coreeng.supportbot.rating.Rating;
import com.coreeng.supportbot.rating.RatingButtonInput;
import com.coreeng.supportbot.rating.RatingFeedbackViewInput;
import com.coreeng.supportbot.rating.RatingFeedbackViewMapper;
import com.coreeng.supportbot.rating.RatingRequestMessageMapper;
import com.coreeng.supportbot.rating.RatingService;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.coreeng.supportbot.slack.client.SlackGetMessageByTsRequest;
import com.coreeng.supportbot.slack.client.SlackPostEphemeralMessageRequest;
import com.coreeng.supportbot.ticket.TicketId;
import com.coreeng.supportbot.ticket.TicketStatus;
import com.coreeng.supportbot.util.JsonMapper;
import com.slack.api.app_backend.interactive_components.payload.BlockActionPayload;
import com.slack.api.bolt.context.builtin.ActionContext;
import com.slack.api.bolt.request.builtin.BlockActionRequest;
import com.slack.api.methods.request.views.ViewsOpenRequest;
import com.slack.api.model.Message;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RatingActionHandlerTest {
    private static final TicketId TICKET_ID = new TicketId(11);
    private static final UUID RATING_ID = UUID.randomUUID();
    private static final String TRIGGER_ID = "trigger-123";

    private final JsonMapper jsonMapper = new JsonMapper();
    private final RatingFeedbackViewMapper feedbackViewMapper = new RatingFeedbackViewMapper(jsonMapper);

    @Mock
    private RatingService ratingService;

    @Mock
    private SlackClient slackClient;

    @Mock
    private ActionContext context;

    private RatingActionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new RatingActionHandler(
                new RatingRequestMessageMapper(jsonMapper), feedbackViewMapper, ratingService, slackClient);
    }

    @Test
    void saves_rating_then_opens_feedback_modal_and_confirms() throws Exception {
        when(ratingService.save(TICKET_ID, 2)).thenReturn(savedRating(2));
        when(context.getTriggerId()).thenReturn(TRIGGER_ID);
        stubThreadAuthor();

        handler.apply(request(2), context);

        ArgumentCaptor<ViewsOpenRequest> captor = ArgumentCaptor.captor();
        verify(slackClient).viewsOpen(captor.capture());
        ViewsOpenRequest openRequest = captor.getValue();
        assertThat(openRequest.getTriggerId()).isEqualTo(TRIGGER_ID);
        assertThat(openRequest.getView().getCallbackId()).isEqualTo(RatingFeedbackViewMapper.CALLBACK_ID);
        assertThat(feedbackViewMapper.parseViewInput(openRequest.getView().getPrivateMetadata()))
                .isEqualTo(new RatingFeedbackViewInput(RATING_ID, TICKET_ID, 2));
        verify(slackClient).postEphemeralMessage(any(SlackPostEphemeralMessageRequest.class));
    }

    @Test
    void modal_failure_does_not_prevent_confirmation() throws Exception {
        when(ratingService.save(TICKET_ID, 5)).thenReturn(savedRating(5));
        when(context.getTriggerId()).thenReturn(TRIGGER_ID);
        when(slackClient.viewsOpen(any())).thenThrow(new RuntimeException("expired_trigger_id"));
        stubThreadAuthor();

        handler.apply(request(5), context);

        verify(slackClient).postEphemeralMessage(any(SlackPostEphemeralMessageRequest.class));
    }

    @Test
    void rejected_rating_opens_no_modal() throws Exception {
        when(ratingService.save(TICKET_ID, 3)).thenThrow(new IllegalArgumentException("Ticket has already been rated"));

        handler.apply(request(3), context);

        verify(slackClient, never()).viewsOpen(any());
        verify(slackClient, never()).postEphemeralMessage(any());
    }

    private void stubThreadAuthor() {
        Message author = new Message();
        author.setUser("U_AUTHOR");
        when(slackClient.getMessageByTs(any(SlackGetMessageByTsRequest.class))).thenReturn(author);
    }

    private BlockActionRequest request(int rating) {
        BlockActionPayload.Action action = new BlockActionPayload.Action();
        action.setActionId(RatingActionHandler.actionId(rating));
        action.setValue(jsonMapper.toJsonString(new RatingButtonInput(TICKET_ID, rating)));

        BlockActionPayload.Channel channel = new BlockActionPayload.Channel();
        channel.setId("C123");
        Message message = new Message();
        message.setTs("1700000001.000200");
        message.setThreadTs("1700000000.000100");

        BlockActionRequest request = mock(BlockActionRequest.class);
        when(request.getPayload())
                .thenReturn(BlockActionPayload.builder()
                        .actions(List.of(action))
                        .channel(channel)
                        .message(message)
                        .build());
        return request;
    }

    private static Rating savedRating(int rating) {
        return Rating.builder()
                .id(RATING_ID)
                .rating(rating)
                .submittedTs("1700000000")
                .status(TicketStatus.closed)
                .build();
    }
}
