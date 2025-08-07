package com.coreeng.supportbot.ticket.handler;

import com.coreeng.supportbot.slack.MessageRef;
import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.coreeng.supportbot.slack.client.SlackGetMessageByTsRequest;
import com.coreeng.supportbot.ticket.Ticket;
import com.coreeng.supportbot.ticket.TicketId;
import com.coreeng.supportbot.ticket.TicketRepository;
import com.coreeng.supportbot.ticket.TicketStatus;
import com.coreeng.supportbot.ticket.TicketStatusChanged;
import com.coreeng.supportbot.ticket.slack.TicketSlackService;
import com.slack.api.model.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RatingEventHandlerTest {

    @Mock
    private TicketSlackService slackService;
    
    @Mock
    private TicketRepository ticketRepository;
    
    @Mock
    private SlackClient slackClient;
    
    @Mock
    private Ticket ticket;
    
    @Mock
    private Message originalMessage;
    
    private RatingEventHandler handler;
    
    private TicketId ticketId;
    private MessageRef queryRef;
    private String userId;
    
    @BeforeEach
    void setUp() {
        handler = new RatingEventHandler(slackService, ticketRepository, slackClient);
        ticketId = new TicketId(12345);
        queryRef = new MessageRef(new MessageTs("1754593060", false), "C1234567890");
        userId = "U1234567890";
    }
    
    @Test
    void shouldPostRatingRequestWhenTicketClosed() {
        // Given
        TicketStatusChanged event = new TicketStatusChanged(ticketId, TicketStatus.closed);
        
        when(ticketRepository.findTicketById(ticketId)).thenReturn(ticket);
        when(ticket.queryRef()).thenReturn(queryRef);
        when(slackClient.getMessageByTs(any(SlackGetMessageByTsRequest.class))).thenReturn(originalMessage);
        when(originalMessage.getUser()).thenReturn(userId);
        
        // When
        handler.onTicketStatusChange(event);
        
        // Then
        verify(ticketRepository).findTicketById(ticketId);
        verify(slackClient).getMessageByTs(SlackGetMessageByTsRequest.of(queryRef));
        verify(slackService).postRatingRequest(queryRef, ticketId, userId);
    }
    
    @Test
    void shouldIgnoreNonClosedTicketEvents() {
        // Given
        TicketStatusChanged event = new TicketStatusChanged(ticketId, TicketStatus.opened);
        
        // When
        handler.onTicketStatusChange(event);
        
        // Then
        verifyNoInteractions(ticketRepository, slackClient, slackService);
    }
    
    @Test
    void shouldHandleTicketNotFound() {
        // Given
        TicketStatusChanged event = new TicketStatusChanged(ticketId, TicketStatus.closed);
        when(ticketRepository.findTicketById(ticketId)).thenReturn(null);
        
        // When
        handler.onTicketStatusChange(event);
        
        // Then
        verify(ticketRepository).findTicketById(ticketId);
        verifyNoInteractions(slackClient, slackService);
    }
    
    @Test
    void shouldHandleSlackClientException() {
        // Given
        TicketStatusChanged event = new TicketStatusChanged(ticketId, TicketStatus.closed);
        
        when(ticketRepository.findTicketById(ticketId)).thenReturn(ticket);
        when(ticket.queryRef()).thenReturn(queryRef);
        when(slackClient.getMessageByTs(any(SlackGetMessageByTsRequest.class)))
                .thenThrow(new RuntimeException("Slack API error"));
        
        // When
        handler.onTicketStatusChange(event);
        
        // Then
        verify(ticketRepository).findTicketById(ticketId);
        verify(slackClient).getMessageByTs(SlackGetMessageByTsRequest.of(queryRef));
        verifyNoInteractions(slackService);
    }

    
    @Test
    void shouldPostRatingRequestWithCorrectParameters() {
        // Given
        TicketStatusChanged event = new TicketStatusChanged(ticketId, TicketStatus.closed);
        
        when(ticketRepository.findTicketById(ticketId)).thenReturn(ticket);
        when(ticket.queryRef()).thenReturn(queryRef);
        when(slackClient.getMessageByTs(any(SlackGetMessageByTsRequest.class))).thenReturn(originalMessage);
        when(originalMessage.getUser()).thenReturn(userId);
        
        // When
        handler.onTicketStatusChange(event);
        
        // Then - verify rating request is called with exactly the right parameters
        verify(slackService).postRatingRequest(queryRef, ticketId, userId);
        verify(slackService, times(1)).postRatingRequest(any(MessageRef.class), any(TicketId.class), any(String.class));
    }
    
    @Test
    void shouldNotPostRatingRequestForNonClosedStatuses() {
        // Given - test each non-closed status
        TicketId testTicketId = new TicketId(99999);
        
        // When - test opened status
        handler.onTicketStatusChange(new TicketStatusChanged(testTicketId, TicketStatus.opened));
        
        // When - test stale status  
        handler.onTicketStatusChange(new TicketStatusChanged(testTicketId, TicketStatus.stale));
        
        // Then - verify no rating requests were made
        verify(slackService, never()).postRatingRequest(any(MessageRef.class), any(TicketId.class), any(String.class));
        verifyNoInteractions(ticketRepository, slackClient);
    }
}
