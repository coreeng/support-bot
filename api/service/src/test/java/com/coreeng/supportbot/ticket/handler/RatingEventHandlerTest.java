package com.coreeng.supportbot.ticket.handler;

import static java.util.Objects.requireNonNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.coreeng.supportbot.escalation.EscalationQueryService;
import com.coreeng.supportbot.slack.MessageRef;
import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.coreeng.supportbot.slack.client.SlackGetMessageByTsRequest;
import com.coreeng.supportbot.ticket.Ticket;
import com.coreeng.supportbot.ticket.TicketId;
import com.coreeng.supportbot.ticket.TicketInMemoryRepository;
import com.coreeng.supportbot.ticket.TicketStatus;
import com.coreeng.supportbot.ticket.TicketStatusChanged;
import com.coreeng.supportbot.ticket.slack.TicketSlackService;
import com.slack.api.model.Message;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RatingEventHandlerTest {

    @Mock
    private TicketSlackService slackService;

    @Mock
    private SlackClient slackClient;

    @Mock
    private Message originalMessage;

    @Mock
    private EscalationQueryService escalationQueryService;

    private TicketInMemoryRepository ticketRepository;
    private RatingEventHandler handler;

    private String userId;

    @BeforeEach
    void setUp() {
        ticketRepository = new TicketInMemoryRepository(escalationQueryService, ZoneId.of("UTC"));
        handler = new RatingEventHandler(slackService, ticketRepository, slackClient);
        userId = "U1234567890";
    }

    @Test
    void shouldPostRatingRequestWhenTicketClosed() {
        // Given: create a real ticket in memory repo
        Ticket ticket = Ticket.builder()
                .channelId("C1234567890")
                .queryTs(new MessageTs("1754593060", false))
                .status(TicketStatus.opened)
                .build();
        Ticket created = ticketRepository.createTicketIfNotExists(ticket);
        TicketId createdId = requireNonNull(created.id());
        MessageRef queryRef = created.queryRef();

        TicketStatusChanged event = new TicketStatusChanged(createdId, TicketStatus.closed);

        when(slackClient.getMessageByTs(any(SlackGetMessageByTsRequest.class))).thenReturn(originalMessage);
        when(originalMessage.getUser()).thenReturn(userId);

        // When
        handler.onTicketStatusChange(event);

        // Then
        verify(slackClient).getMessageByTs(SlackGetMessageByTsRequest.of(queryRef));
        verify(slackService).postRatingRequest(queryRef, createdId, userId);
    }

    @Test
    void shouldIgnoreNonClosedTicketEvents() {
        // Given
        TicketId someId = new TicketId(42);

        // When
        handler.onTicketStatusChange(new TicketStatusChanged(someId, TicketStatus.opened));

        // Then
        verifyNoInteractions(slackClient, slackService);
    }

    @Test
    void shouldHandleTicketNotFound() {
        // Given
        TicketId missing = new TicketId(654_321);
        TicketStatusChanged event = new TicketStatusChanged(missing, TicketStatus.closed);

        // When
        handler.onTicketStatusChange(event);

        // Then
        verifyNoInteractions(slackClient, slackService);
    }

    @Test
    void shouldHandleSlackClientException() {
        // Given
        Ticket ticket = Ticket.builder()
                .channelId("C123")
                .queryTs(new MessageTs("1754593000", false))
                .status(TicketStatus.opened)
                .build();
        Ticket created = ticketRepository.createTicketIfNotExists(ticket);
        TicketId createdId = requireNonNull(created.id());
        MessageRef queryRef = created.queryRef();

        TicketStatusChanged event = new TicketStatusChanged(createdId, TicketStatus.closed);

        when(slackClient.getMessageByTs(any(SlackGetMessageByTsRequest.class)))
                .thenThrow(new RuntimeException("Slack API error"));

        // When
        handler.onTicketStatusChange(event);

        // Then
        verify(slackClient).getMessageByTs(SlackGetMessageByTsRequest.of(queryRef));
        verifyNoInteractions(slackService);
    }

    @Test
    void shouldPostRatingRequestWithCorrectParameters() {
        // Given
        Ticket ticket = Ticket.builder()
                .channelId("C123")
                .queryTs(new MessageTs("1754593100", false))
                .status(TicketStatus.opened)
                .build();
        Ticket created = ticketRepository.createTicketIfNotExists(ticket);
        TicketId createdId = requireNonNull(created.id());
        MessageRef queryRef = created.queryRef();

        TicketStatusChanged event = new TicketStatusChanged(createdId, TicketStatus.closed);
        when(slackClient.getMessageByTs(any(SlackGetMessageByTsRequest.class))).thenReturn(originalMessage);
        when(originalMessage.getUser()).thenReturn(userId);

        // When
        handler.onTicketStatusChange(event);

        // Then - verify rating request is called with exactly the right parameters
        verify(slackService).postRatingRequest(queryRef, createdId, userId);
        verify(slackService, times(1)).postRatingRequest(any(MessageRef.class), any(TicketId.class), any(String.class));
    }

    @Test
    void shouldNotPostRatingRequestForNonClosedStatuses() {
        // Given - test each non-closed status
        TicketId testTicketId = new TicketId(99_999);

        // When - test opened status
        handler.onTicketStatusChange(new TicketStatusChanged(testTicketId, TicketStatus.opened));

        // When - test stale status
        handler.onTicketStatusChange(new TicketStatusChanged(testTicketId, TicketStatus.stale));

        // Then - verify no rating requests were made
        verify(slackService, never()).postRatingRequest(any(MessageRef.class), any(TicketId.class), any(String.class));
        verifyNoInteractions(slackClient);
    }
}
