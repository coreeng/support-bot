package com.coreeng.supportbot;

import com.coreeng.supportbot.config.EnumProps;
import com.coreeng.supportbot.config.SlackTicketsProps;
import com.coreeng.supportbot.enums.EnumsService;
import com.coreeng.supportbot.escalation.EscalationInMemoryRepository;
import com.coreeng.supportbot.escalation.EscalationQueryService;
import com.coreeng.supportbot.slack.MessageRef;
import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.slack.events.MessagePosted;
import com.coreeng.supportbot.slack.events.ReactionAdded;
import com.coreeng.supportbot.ticket.Ticket;
import com.coreeng.supportbot.ticket.TicketCreatedMessage;
import com.coreeng.supportbot.ticket.TicketInMemoryRepository;
import com.coreeng.supportbot.ticket.TicketProcessingService;
import com.coreeng.supportbot.ticket.TicketRepository;
import com.coreeng.supportbot.ticket.TicketStatus;
import com.coreeng.supportbot.ticket.slack.TicketSlackService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TicketProcessingServiceTests {
    private static final MessageTs messageTs = MessageTs.of("some-message-ts");
    private static final String userId = "some-user-id";

    private TicketProcessingService ticketProcessingService;
    private TicketRepository ticketRepository;
    @Mock
    private TicketSlackService slackService;
    private SlackTicketsProps slackTicketsProps;
    @Mock
    private ApplicationEventPublisher publisher;

    @Captor
    private ArgumentCaptor<TicketCreatedMessage> createdMessageCaptor;

    @BeforeEach
    public void setUp() {
        ZoneId timezone = ZoneId.of("UTC");
        EscalationQueryService escalationQueryService = new EscalationQueryService(new EscalationInMemoryRepository(timezone));
        ticketRepository = new TicketInMemoryRepository(escalationQueryService, timezone);
        slackTicketsProps = new SlackTicketsProps(
            "some-channel-id",
            "eyes",
            "ticket"
        );
        EnumsService enumsService = new EnumsService(new EnumProps(List.of(), List.of(), List.of()));
        ticketProcessingService = new TicketProcessingService(
            ticketRepository,
            slackService,
            escalationQueryService,
            slackTicketsProps,
            enumsService, enumsService, enumsService,
            publisher
        );
    }

    @Test
    public void shouldCreateQueryOnMessage() {
        // when
        ticketProcessingService.handleMessagePosted(new MessagePosted(
            "some message",
            userId,
            new MessageRef(
                messageTs,
                null,
                slackTicketsProps.channelId()
            )
        ));

        // then
        assertTrue(ticketRepository.queryExists(messageTs), "Query is created");
    }

    @Test
    public void shouldIgnoreMessageToDifferentChannel() {
        // when
        ticketProcessingService.handleMessagePosted(new MessagePosted(
            "some message",
            userId,
            new MessageRef(
                messageTs,
                null,
                "some-random-channnel"
            )
        ));

        // then
        assertFalse(ticketRepository.queryExists(messageTs), "Event is ignored");
    }

    @Test
    public void shouldIgnoreMessageInThreads() {
        // when
        ticketProcessingService.handleMessagePosted(new MessagePosted(
            "some message",
            userId,
            new MessageRef(
                messageTs,
                MessageTs.of("thread-ts"),
                slackTicketsProps.channelId()
            )
        ));

        // then
        assertFalse(ticketRepository.queryExists(messageTs), "Event is ignored");
    }

    @Test
    public void shouldCreateTicketOnEyes() {
        // given
        MessageTs postedMessageTs = MessageTs.of("posted-message-ts");

        MessageRef threadRef = new MessageRef(
            messageTs,
            null,
            slackTicketsProps.channelId()
        );
        MessageRef ticketFormRef = new MessageRef(
            postedMessageTs, messageTs, slackTicketsProps.channelId()
        );
        when(slackService.postTicketForm(eq(threadRef), createdMessageCaptor.capture())).thenReturn(ticketFormRef);

        // when
        ticketProcessingService.handleReactionAdded(new ReactionAdded(
            slackTicketsProps.expectedInitialReaction(),
            userId,
            threadRef
        ));

        // then
        assertTrue(ticketRepository.queryExists(messageTs), "Query is created");

        Ticket ticket = ticketRepository.findTicketByQuery(messageTs);
        assertNotNull(ticket, "Ticket is created");
        assertNotNull(ticket.id());
        assertEquals(TicketStatus.opened, ticket.status());
        assertEquals(messageTs, ticket.queryTs());

        verify(slackService, description("Post is tracked")).markPostTracked(threadRef);
        verify(slackService, description("Ticket form is posted"))
            .postTicketForm(eq(threadRef), createdMessageCaptor.capture());
    }
}
