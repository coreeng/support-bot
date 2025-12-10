package com.coreeng.supportbot.ticket.handler;

import com.coreeng.supportbot.rbac.RbacRestrictionMessage;
import com.coreeng.supportbot.rbac.RbacService;
import com.coreeng.supportbot.slack.SlackId;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.coreeng.supportbot.slack.client.SlackPostEphemeralMessageRequest;
import com.coreeng.supportbot.ticket.EscalateViewMapper;
import com.coreeng.supportbot.ticket.TicketSummaryService;
import com.coreeng.supportbot.ticket.TicketSummaryViewMapper;
import com.slack.api.app_backend.interactive_components.payload.BlockActionPayload;
import com.slack.api.bolt.context.builtin.ActionContext;
import com.slack.api.bolt.request.builtin.BlockActionRequest;
import com.slack.api.model.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketActionsHandlerTest {
    @Mock
    private TicketSummaryViewMapper summaryViewMapper;
    @Mock
    private EscalateViewMapper escalateViewMapper;
    @Mock
    private SlackClient slackClient;
    @Mock
    private TicketSummaryService ticketSummaryService;
    @Mock
    private RbacService rbacService;

    private TicketActionsHandler handler;

    @BeforeEach
    void setUp() {
        handler = new TicketActionsHandler(
            summaryViewMapper,
            escalateViewMapper,
            slackClient,
            ticketSummaryService,
            rbacService
        );
    }

    @Test
    void whenUserIsNotSupport_sendsRestrictionMessage() {
        // given
        String userId = "U123456";
        String channelId = "C123456";
        String threadTs = "1234567890.123456";

        BlockActionPayload.Channel channel = new BlockActionPayload.Channel();
        channel.setId(channelId);
        Message message = new Message();
        message.setThreadTs(threadTs);

        ActionContext context = mock(ActionContext.class);
        BlockActionRequest request = mock(BlockActionRequest.class);
        BlockActionPayload payload = BlockActionPayload.builder()
            .channel(channel)
            .message(message)
            .build();

        when(rbacService.isSupportBySlackId(SlackId.user(userId))).thenReturn(false);
        when(context.getRequestUserId()).thenReturn(userId);
        when(request.getPayload()).thenReturn(payload);

        // when
        handler.apply(request, context);

        // then
        verify(rbacService).isSupportBySlackId(SlackId.user(userId));

        ArgumentCaptor<SlackPostEphemeralMessageRequest> messageRequestCaptor = ArgumentCaptor.captor();
        verify(slackClient).postEphemeralMessage(messageRequestCaptor.capture());

        SlackPostEphemeralMessageRequest messagePostRequest = messageRequestCaptor.getValue();
        assertEquals(userId, messagePostRequest.userId());
        assertEquals(channelId, messagePostRequest.channel());
        assertNotNull(messagePostRequest.threadTs());
        assertEquals(threadTs, messagePostRequest.threadTs().ts());
        assertEquals(new RbacRestrictionMessage(), messagePostRequest.message());

        verifyNoInteractions(summaryViewMapper, escalateViewMapper, ticketSummaryService);
    }

    @Test
    void whenUserIsSupport_processesRequest() {
        // given
        String userId = "U123456";

        ActionContext context = mock(ActionContext.class);
        BlockActionRequest request = mock(BlockActionRequest.class);
        BlockActionPayload payload = BlockActionPayload.builder()
            .actions(List.of())
            .build();

        when(context.getRequestUserId()).thenReturn(userId);
        when(rbacService.isSupportBySlackId(SlackId.user(userId))).thenReturn(true);
        when(request.getPayload()).thenReturn(payload);

        // when
        handler.apply(request, context);

        // then
        verify(rbacService).isSupportBySlackId(SlackId.user(userId));
        verifyNoInteractions(summaryViewMapper, escalateViewMapper, ticketSummaryService, slackClient);
    }
} 