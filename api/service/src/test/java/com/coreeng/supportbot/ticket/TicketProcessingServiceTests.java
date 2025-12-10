package com.coreeng.supportbot;

import com.coreeng.supportbot.config.SlackTicketsProps;
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

import java.time.Instant;
import java.time.ZoneId;

import com.google.common.collect.ImmutableList;

import static org.assertj.core.api.Assertions.assertThat;
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
            "ticket",
            "white_check_mark",
            "rocket"
        );
        ticketProcessingService = new TicketProcessingService(
            ticketRepository,
            slackService,
            escalationQueryService,
            slackTicketsProps,
            publisher
        );
    }

    @Test
    public void shouldCreateQueryOnMessage() {
        // when
        MessageRef threadRef = new MessageRef(
            messageTs,
            null,
            slackTicketsProps.channelId()
        );
        ticketProcessingService.handleMessagePosted(new MessagePosted(
            "some message",
            userId,
            threadRef
        ));

        // then
        assertTrue(ticketRepository.queryExists(threadRef), "Query is created");
    }

    @Test
    public void shouldIgnoreMessageToDifferentChannel() {
        // when
        MessageRef threadRef = new MessageRef(
            messageTs,
            null,
            "some-random-channnel"
        );
        ticketProcessingService.handleMessagePosted(new MessagePosted(
            "some message",
            userId,
            threadRef
        ));

        // then
        assertFalse(ticketRepository.queryExists(threadRef), "Event is ignored");
    }

    @Test
    public void shouldIgnoreMessageInThreads() {
        // when
        MessageRef threadRef = new MessageRef(
            messageTs,
            MessageTs.of("thread-ts"),
            slackTicketsProps.channelId()
        );
        ticketProcessingService.handleMessagePosted(new MessagePosted(
            "some message",
            userId,
            threadRef
        ));

        // then
        assertFalse(ticketRepository.queryExists(threadRef), "Event is ignored");
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
        assertTrue(ticketRepository.queryExists(threadRef), "Query is created");

        Ticket ticket = ticketRepository.findTicketByQuery(threadRef);
        assertNotNull(ticket, "Ticket is created");
        assertNotNull(ticket.id());
        assertEquals(TicketStatus.opened, ticket.status());
        assertEquals(messageTs, ticket.queryTs());

        verify(slackService, description("Post is tracked")).markPostTracked(threadRef);
        verify(slackService, description("Ticket form is posted"))
            .postTicketForm(eq(threadRef), createdMessageCaptor.capture());
    }

    @Test
    void calculateSecondsSinceCreation_returnsPositiveDuration() {
        // given
        MessageTs oneSecondAgo = MessageTs.of(String.valueOf(
            (System.currentTimeMillis() / 1000.0) - 1
        ));
        Ticket ticket = Ticket.createNew(oneSecondAgo, slackTicketsProps.channelId());

        // when
        Long seconds = ticketProcessingService.calculateSecondsSinceCreation(ticket);

        // then
        assertThat(seconds).isNotNull();
        assertThat(seconds).isGreaterThanOrEqualTo(0L);
    }

    @Test
    void calculateSecondsSinceCreation_returnsNullForNullQueryTs() {
        Ticket ticket = Ticket.builder()
            .channelId(slackTicketsProps.channelId())
            .status(TicketStatus.opened)
            .queryTs(null)
            .build();

        // when
        Long seconds = ticketProcessingService.calculateSecondsSinceCreation(ticket);

        // then - should return null, not throw
        assertThat(seconds).isNull();
    }

    @Test
    void calculateSecondsSinceCreation_returnsNullForInvalidTimestamp() {
        // given
        Ticket ticket = Ticket.builder()
            .channelId(slackTicketsProps.channelId())
            .status(TicketStatus.opened)
            .queryTs(MessageTs.of("invalid-timestamp"))
            .build();

        // when
        Long seconds = ticketProcessingService.calculateSecondsSinceCreation(ticket);

        // then
        assertThat(seconds).isNull();
    }

    @Test
    void ticketOperations_continueWhenDurationCalculationFails() {
        // given - ticket with empty statusLog (no 'opened' status)
        MessageTs queryTs = MessageTs.of(String.valueOf(System.currentTimeMillis() / 1000.0));
        Ticket ticket = ticketRepository.createTicketIfNotExists(
            Ticket.builder()
                .channelId(slackTicketsProps.channelId())
                .queryTs(queryTs)
                .status(TicketStatus.opened)
                .statusLog(ImmutableList.of())
                .lastInteractedAt(Instant.now())
                .build()
        );

        // Duration returns null when no 'opened' status in statusLog
        Long duration = ticketProcessingService.calculateSecondsSinceCreation(ticket);
        assertThat(duration).isNull();

        // when
        ticketProcessingService.markAsStale(ticket.id());

        // then
        // operation completed successfully even with duration cacl failures
        Ticket updatedTicket = ticketRepository.findTicketById(ticket.id());
        assertThat(updatedTicket.status()).isEqualTo(TicketStatus.stale);
        verify(publisher).publishEvent(any(Object.class)); // Event was still published
        verify(slackService).warnStaleness(any(MessageRef.class)); // Slack notification was still sent
    }
}
