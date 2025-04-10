package com.coreeng.supportbot.escalation.handler;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.coreeng.supportbot.rbac.RbacRestrictionMessage;
import com.slack.api.model.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.coreeng.supportbot.escalation.EscalationCreatedMessageMapper;
import com.coreeng.supportbot.escalation.EscalationProcessingService;
import com.coreeng.supportbot.rbac.RbacService;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.coreeng.supportbot.slack.client.SlackPostEphemeralMessageRequest;
import com.slack.api.app_backend.interactive_components.payload.BlockActionPayload;
import com.slack.api.bolt.context.builtin.ActionContext;
import com.slack.api.bolt.request.builtin.BlockActionRequest;
import com.slack.api.methods.SlackApiException;

@ExtendWith(MockitoExtension.class)
class EscalationActionHandlerTest {
    @Mock
    private EscalationProcessingService processingService;
    @Mock
    private EscalationCreatedMessageMapper createdMessageMapper;
    @Mock
    private RbacService rbacService;
    @Mock
    private SlackClient slackClient;

    private EscalationActionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new EscalationActionHandler(
            processingService,
            createdMessageMapper,
            rbacService,
            slackClient
        );
    }

    @Test
    void whenUserIsNotSupport_sendsRestrictionMessage() throws IOException, SlackApiException {
        // given
        String userId = "U123456";
        String channelId = "C123456";
        String threadTs = "1234567890.123456";
        
        BlockActionRequest request = mock(BlockActionRequest.class);
        ActionContext context = mock(ActionContext.class);
        BlockActionPayload.User user = new BlockActionPayload.User();
        user.setId(userId);
        BlockActionPayload.Channel channel = new BlockActionPayload.Channel();
        channel.setId(channelId);
        Message message = new Message();
        message.setThreadTs(threadTs);
        BlockActionPayload payload = BlockActionPayload.builder()
            .user(user)
            .channel(channel)
            .message(message)
            .build();

        when(request.getPayload()).thenReturn(payload);
        when(context.getRequestUserId()).thenReturn(userId);
        when(rbacService.isSupportBySlackId(userId)).thenReturn(false);
        
        // when
        handler.apply(request, context);

        // then
        verify(rbacService).isSupportBySlackId(userId);

        ArgumentCaptor<SlackPostEphemeralMessageRequest> messageRequestCaptor = ArgumentCaptor.captor();
        verify(slackClient).postEphemeralMessage(messageRequestCaptor.capture());
        
        SlackPostEphemeralMessageRequest postMessageRequest = messageRequestCaptor.getValue();
        assertEquals(userId, postMessageRequest.userId());
        assertEquals(channelId, postMessageRequest.channel());
        assertNotNull(postMessageRequest.threadTs());
        assertEquals(threadTs, postMessageRequest.threadTs().ts());
        assertEquals(new RbacRestrictionMessage(), postMessageRequest.message());
        
        verifyNoInteractions(processingService, createdMessageMapper);
    }

    @Test
    void whenUserIsSupport_processesRequest() throws IOException, SlackApiException {
        // given
        String userId = "U123456";
        
        BlockActionRequest request = mock(BlockActionRequest.class);
        ActionContext context = mock(ActionContext.class);
        BlockActionPayload.User user = new BlockActionPayload.User();
        user.setId(userId);
        BlockActionPayload payload = BlockActionPayload.builder()
            .user(user)
            .actions(List.of())
            .build();

        when(request.getPayload()).thenReturn(payload);
        when(rbacService.isSupportBySlackId(userId)).thenReturn(true);
        
        // when
        handler.apply(request, context);

        // then
        verify(rbacService).isSupportBySlackId(userId);
        verifyNoInteractions(slackClient);
    }
} 