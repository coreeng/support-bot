package com.coreeng.supportbot.ticket.handler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.coreeng.supportbot.rbac.RbacService;
import com.coreeng.supportbot.slack.SlackId;
import com.coreeng.supportbot.slack.events.ReactionAdded;
import com.coreeng.supportbot.ticket.TicketProcessingService;
import com.slack.api.app_backend.events.payload.EventsApiPayload;
import com.slack.api.app_backend.events.payload.ReactionAddedPayload;
import com.slack.api.bolt.context.builtin.EventContext;
import com.slack.api.model.event.ReactionAddedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReactionAddedHandlerTest {
    @Mock
    private TicketProcessingService ticketProcessingService;

    @Mock
    private RbacService rbacService;

    private ReactionAddedHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ReactionAddedHandler(ticketProcessingService, rbacService);
    }

    @Test
    void whenUserIsNot_supportSkipsProcessing() {
        // given
        String userId = "U123456";
        String reaction = "eyes";

        EventsApiPayload<ReactionAddedEvent> eventPayload = new ReactionAddedPayload();
        ReactionAddedEvent event = new ReactionAddedEvent();
        event.setUser(userId);
        event.setReaction(reaction);
        eventPayload.setEvent(event);
        EventContext context = mock(EventContext.class);

        when(rbacService.isSupportBySlackId(SlackId.user(userId))).thenReturn(false);

        // when
        handler.apply(eventPayload, context);

        // then
        verify(rbacService).isSupportBySlackId(SlackId.user(userId));
        verifyNoInteractions(ticketProcessingService);
    }

    @Test
    void whenUserIsSupport_processesReaction() {
        // given
        String userId = "U123456";
        String reaction = "eyes";
        String messageTs = "123456.789";
        String threadTs = "987654.321";
        String channelId = "U123456";

        EventsApiPayload<ReactionAddedEvent> eventPayload = new ReactionAddedPayload();
        ReactionAddedEvent event = new ReactionAddedEvent();
        event.setUser(userId);
        event.setReaction(reaction);
        ReactionAddedEvent.Item item = new ReactionAddedEvent.Item();
        item.setTs(messageTs);
        item.setChannel(channelId);
        event.setItem(item);
        eventPayload.setEvent(event);
        EventContext context = mock(EventContext.class);

        when(context.getThreadTs()).thenReturn(threadTs);
        when(rbacService.isSupportBySlackId(SlackId.user(userId))).thenReturn(true);

        // when
        handler.apply(eventPayload, context);

        // then
        verify(rbacService).isSupportBySlackId(SlackId.user(userId));
        verify(ticketProcessingService).handleReactionAdded(any(ReactionAdded.class));
    }
}
