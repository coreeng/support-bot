package com.coreeng.supportbot.ticket.rest;

import com.coreeng.supportbot.config.TicketAssignmentProps;
import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.slack.SlackException;
import com.coreeng.supportbot.slack.SlackId;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.coreeng.supportbot.slack.client.SlackGetMessageByTsRequest;
import com.coreeng.supportbot.slack.client.SlackPostMessageRequest;
import com.coreeng.supportbot.ticket.Ticket;
import com.coreeng.supportbot.ticket.TicketId;
import com.coreeng.supportbot.ticket.TicketRepository;
import com.coreeng.supportbot.ticket.TicketStatus;
import com.coreeng.supportbot.ticket.TicketsQuery;
import com.coreeng.supportbot.util.Page;
import com.google.common.collect.ImmutableList;
import com.slack.api.methods.response.conversations.ConversationsOpenResponse;
import com.slack.api.model.Conversation;
import org.jooq.exception.DataAccessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BulkReassignmentServiceTest {

    @Mock
    private TicketAssignmentProps assignmentProps;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private com.coreeng.supportbot.slack.client.SlackClient slackClient;

    @InjectMocks
    private BulkReassignmentService service;

    @BeforeEach
    void setUp() {
        service = new BulkReassignmentService(assignmentProps, ticketRepository, slackClient);
        // Lenient stubbing for SlackClient - notifications are optional and shouldn't break tests
        ConversationsOpenResponse dmResponse = 
            mock(ConversationsOpenResponse.class);
        Conversation channel = mock(Conversation.class);
        lenient().when(channel.getId()).thenReturn("D12345");
        lenient().when(dmResponse.isOk()).thenReturn(true);
        lenient().when(dmResponse.getChannel()).thenReturn(channel);
        lenient().when(slackClient.openDmConversation(any())).thenReturn(dmResponse);
        lenient().when(slackClient.getPermalink(any())).thenReturn("https://slack.com/permalink");
        lenient().when(slackClient.postMessage(any())).thenReturn(null);
    }

    @Test
    void shouldRejectBulkReassignWhenAssignmentIsDisabled() {
        // given
        when(assignmentProps.enabled()).thenReturn(false);
        BulkReassignRequest request = new BulkReassignRequest(
            List.of(new TicketId(1), new TicketId(2)),
            "U12345"
        );

        // when
        BulkReassignResultUI result = service.bulkReassign(request);

        // then
        assertThat(result.successCount()).isEqualTo(0);
        assertThat(result.skippedCount()).isEqualTo(0);
        assertThat(result.message()).isEqualTo("Assignment feature is disabled");

        verify(assignmentProps).enabled();
        verifyNoInteractions(ticketRepository);
    }

    @Test
    void shouldRejectBulkReassignWhenTicketIdsEmpty() {
        // given
        when(assignmentProps.enabled()).thenReturn(true);
        BulkReassignRequest request = new BulkReassignRequest(
            List.of(),
            "U12345"
        );

        // when
        BulkReassignResultUI result = service.bulkReassign(request);

        // then
        assertThat(result.successCount()).isEqualTo(0);
        assertThat(result.skippedCount()).isEqualTo(0);
        assertThat(result.message()).isEqualTo("Ticket IDs list cannot be empty");

        verify(assignmentProps).enabled();
        verifyNoInteractions(ticketRepository);
    }

    @Test
    void shouldRejectBulkReassignWhenAssignedToIsBlank() {
        // given
        when(assignmentProps.enabled()).thenReturn(true);
        BulkReassignRequest request = new BulkReassignRequest(
            List.of(new TicketId(1), new TicketId(2)),
            ""
        );

        // when
        BulkReassignResultUI result = service.bulkReassign(request);

        // then
        assertThat(result.successCount()).isEqualTo(0);
        assertThat(result.skippedCount()).isEqualTo(0);
        assertThat(result.message()).isEqualTo("Assignee Slack ID is required");

        verify(assignmentProps).enabled();
        verifyNoInteractions(ticketRepository);
    }

    @Test
    void shouldSuccessfullyReassignAllOpenTickets() {
        // given
        TicketId ticket1 = new TicketId(1);
        TicketId ticket2 = new TicketId(2);
        TicketId ticket3 = new TicketId(3);
        String assignedTo = "U12345";

        Ticket openTicket1 = createTicket(ticket1, TicketStatus.opened);
        Ticket openTicket2 = createTicket(ticket2, TicketStatus.opened);
        Ticket openTicket3 = createTicket(ticket3, TicketStatus.opened);

        when(assignmentProps.enabled()).thenReturn(true);
        when(ticketRepository.listTickets(ArgumentMatchers.any(TicketsQuery.class)))
            .thenReturn(new Page<>(ImmutableList.of(openTicket1, openTicket2, openTicket3), 0, 1, 3));

        BulkReassignRequest request = new BulkReassignRequest(
            List.of(ticket1, ticket2, ticket3),
            assignedTo
        );

        // when
        BulkReassignResultUI result = service.bulkReassign(request);

        // then
        assertThat(result.successCount()).isEqualTo(3);
        assertThat(result.successfulTicketIds()).containsExactly(ticket1, ticket2, ticket3);
        assertThat(result.skippedCount()).isEqualTo(0);
        assertThat(result.skippedTicketIds()).isEmpty();
        assertThat(result.message()).isEqualTo("All tickets successfully reassigned");

        verify(assignmentProps).enabled();
        verify(ticketRepository).listTickets(ArgumentMatchers.any(TicketsQuery.class));
        verify(ticketRepository).assign(ticket1, assignedTo);
        verify(ticketRepository).assign(ticket2, assignedTo);
        verify(ticketRepository).assign(ticket3, assignedTo);
        verifyNoMoreInteractions(assignmentProps, ticketRepository);
    }

    @Test
    void shouldPartiallySucceedWhenSomeTicketsFailToReassign() {
        // given
        TicketId ticket1 = new TicketId(1);
        TicketId ticket2 = new TicketId(2);
        TicketId ticket3 = new TicketId(3);
        String assignedTo = "U12345";

        Ticket openTicket1 = createTicket(ticket1, TicketStatus.opened);
        Ticket openTicket2 = createTicket(ticket2, TicketStatus.opened);
        Ticket openTicket3 = createTicket(ticket3, TicketStatus.opened);

        when(assignmentProps.enabled()).thenReturn(true);
        when(ticketRepository.listTickets(ArgumentMatchers.any(TicketsQuery.class)))
            .thenReturn(new Page<>(ImmutableList.of(openTicket1, openTicket2, openTicket3), 0, 1, 3));
        when(ticketRepository.assign(ticket1, assignedTo)).thenReturn(true);
        when(ticketRepository.assign(ticket2, assignedTo)).thenThrow(new DataAccessException("Database error"));
        when(ticketRepository.assign(ticket3, assignedTo)).thenReturn(true);

        BulkReassignRequest request = new BulkReassignRequest(
            List.of(ticket1, ticket2, ticket3),
            assignedTo
        );

        // when
        BulkReassignResultUI result = service.bulkReassign(request);

        // then
        assertThat(result.successCount()).isEqualTo(2);
        assertThat(result.successfulTicketIds()).containsExactly(ticket1, ticket3);
        assertThat(result.skippedCount()).isEqualTo(1);
        assertThat(result.skippedTicketIds()).containsExactly(ticket2);
        assertThat(result.message()).isEqualTo("2 of 3 tickets successfully reassigned, 1 skipped");

        verify(assignmentProps).enabled();
        verify(ticketRepository).listTickets(ArgumentMatchers.any(TicketsQuery.class));
        verify(ticketRepository).assign(ticket1, assignedTo);
        verify(ticketRepository).assign(ticket2, assignedTo);
        verify(ticketRepository).assign(ticket3, assignedTo);
        verifyNoMoreInteractions(assignmentProps, ticketRepository);
    }

    @Test
    void shouldSkipClosedTicketsButReassignStaleTickets() {
        // given
        TicketId ticket1 = new TicketId(1);
        TicketId ticket2 = new TicketId(2);
        TicketId ticket3 = new TicketId(3);
        TicketId ticket4 = new TicketId(4);
        String assignedTo = "U12345";

        Ticket openTicket = createTicket(ticket1, TicketStatus.opened);
        // ticket2 is closed, so it's filtered out at the database level
        Ticket staleTicket = createTicket(ticket3, TicketStatus.stale);
        Ticket anotherOpenTicket = createTicket(ticket4, TicketStatus.opened);

        when(assignmentProps.enabled()).thenReturn(true);
        // Database query with excludeClosed=true only returns non-closed tickets
        when(ticketRepository.listTickets(ArgumentMatchers.any(TicketsQuery.class)))
            .thenReturn(new Page<>(ImmutableList.of(openTicket, staleTicket, anotherOpenTicket), 0, 1, 3));

        BulkReassignRequest request = new BulkReassignRequest(
            List.of(ticket1, ticket2, ticket3, ticket4),
            assignedTo
        );

        // when
        BulkReassignResultUI result = service.bulkReassign(request);

        // then
        assertThat(result.successCount()).isEqualTo(3);
        assertThat(result.successfulTicketIds()).containsExactly(ticket1, ticket3, ticket4);
        assertThat(result.skippedCount()).isEqualTo(1);
        assertThat(result.skippedTicketIds()).containsExactly(ticket2);
        assertThat(result.message()).isEqualTo("3 of 4 tickets successfully reassigned, 1 skipped");

        verify(assignmentProps).enabled();
        verify(ticketRepository).listTickets(ArgumentMatchers.any(TicketsQuery.class));
        verify(ticketRepository).assign(ticket1, assignedTo);
        verify(ticketRepository).assign(ticket3, assignedTo);
        verify(ticketRepository).assign(ticket4, assignedTo);
        verify(ticketRepository, never()).assign(ticket2, assignedTo);
        verifyNoMoreInteractions(assignmentProps, ticketRepository);
    }

    @Test
    void shouldSkipNonExistentTickets() {
        // given
        TicketId ticket1 = new TicketId(1);
        TicketId ticket2 = new TicketId(2);
        String assignedTo = "U12345";

        Ticket openTicket = createTicket(ticket1, TicketStatus.opened);

        when(assignmentProps.enabled()).thenReturn(true);
        // ticket2 doesn't exist, so only ticket1 is returned
        when(ticketRepository.listTickets(ArgumentMatchers.any(TicketsQuery.class)))
            .thenReturn(new Page<>(ImmutableList.of(openTicket), 0, 1, 1));

        BulkReassignRequest request = new BulkReassignRequest(
            List.of(ticket1, ticket2),
            assignedTo
        );

        // when
        BulkReassignResultUI result = service.bulkReassign(request);

        // then
        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.successfulTicketIds()).containsExactly(ticket1);
        assertThat(result.skippedCount()).isEqualTo(1);
        assertThat(result.skippedTicketIds()).containsExactly(ticket2);
        assertThat(result.message()).isEqualTo("1 of 2 tickets successfully reassigned, 1 skipped");

        verify(assignmentProps).enabled();
        verify(ticketRepository).listTickets(ArgumentMatchers.any(TicketsQuery.class));
        verify(ticketRepository).assign(ticket1, assignedTo);
        verify(ticketRepository, never()).assign(ticket2, assignedTo);
        verifyNoMoreInteractions(assignmentProps, ticketRepository);
    }

    @Test
    void shouldSendNotificationWhenTicketsAreSuccessfullyAssigned() {
        // given
        TicketId ticket1 = new TicketId(1);
        TicketId ticket2 = new TicketId(2);
        String assignedTo = "U12345";

        Ticket openTicket1 = createTicket(ticket1, TicketStatus.opened);
        Ticket openTicket2 = createTicket(ticket2, TicketStatus.opened);

        ConversationsOpenResponse dmResponse = 
            mock(ConversationsOpenResponse.class);
        Conversation channel = mock(Conversation.class);
        when(channel.getId()).thenReturn("D12345");
        when(dmResponse.isOk()).thenReturn(true);
        when(dmResponse.getChannel()).thenReturn(channel);

        when(assignmentProps.enabled()).thenReturn(true);
        when(ticketRepository.listTickets(ArgumentMatchers.any(TicketsQuery.class)))
            .thenReturn(new Page<>(ImmutableList.of(openTicket1, openTicket2), 0, 1, 2));
        when(slackClient.openDmConversation(SlackId.user(assignedTo))).thenReturn(dmResponse);
        when(slackClient.getPermalink(ArgumentMatchers.any(SlackGetMessageByTsRequest.class)))
            .thenReturn("https://slack.com/permalink1", "https://slack.com/permalink2");

        BulkReassignRequest request = new BulkReassignRequest(
            List.of(ticket1, ticket2),
            assignedTo
        );

        // when
        BulkReassignResultUI result = service.bulkReassign(request);

        // then
        assertThat(result.successCount()).isEqualTo(2);
        
        ArgumentCaptor<SlackPostMessageRequest> messageCaptor = ArgumentCaptor.forClass(SlackPostMessageRequest.class);
        verify(slackClient).openDmConversation(SlackId.user(assignedTo));
        verify(slackClient, times(2)).getPermalink(ArgumentMatchers.any(SlackGetMessageByTsRequest.class));
        verify(slackClient).postMessage(messageCaptor.capture());
        
        SlackPostMessageRequest sentMessage = messageCaptor.getValue();
        assertThat(sentMessage.channel()).isEqualTo("D12345");
        String messageText = sentMessage.message().getText();
        assertThat(messageText).contains("You have been assigned to 2 tickets");
        assertThat(messageText).contains("Ticket ID-1");
        assertThat(messageText).contains("Ticket ID-2");
    }

    @Test
    void shouldNotSendNotificationWhenNoTicketsAreSuccessfullyAssigned() {
        // given
        TicketId ticket1 = new TicketId(1);
        String assignedTo = "U12345";

        Ticket closedTicket = createTicket(ticket1, TicketStatus.closed);

        when(assignmentProps.enabled()).thenReturn(true);
        when(ticketRepository.listTickets(ArgumentMatchers.any(TicketsQuery.class)))
            .thenReturn(new Page<>(ImmutableList.of(closedTicket), 0, 1, 1));

        BulkReassignRequest request = new BulkReassignRequest(
            List.of(ticket1),
            assignedTo
        );

        // when
        BulkReassignResultUI result = service.bulkReassign(request);

        // then
        assertThat(result.successCount()).isEqualTo(0);
        verify(slackClient, never()).openDmConversation(any());
        verify(slackClient, never()).postMessage(any());
    }

    @Test
    void shouldNotSendNotificationWhenDmConversationFailsToOpen() {
        // given
        TicketId ticket1 = new TicketId(1);
        String assignedTo = "U12345";

        Ticket openTicket = createTicket(ticket1, TicketStatus.opened);

        ConversationsOpenResponse dmResponse = 
            mock(ConversationsOpenResponse.class);
        when(dmResponse.isOk()).thenReturn(false);
        when(dmResponse.getError()).thenReturn("messages_tab_disabled");

        when(assignmentProps.enabled()).thenReturn(true);
        when(ticketRepository.listTickets(ArgumentMatchers.any(TicketsQuery.class)))
            .thenReturn(new Page<>(ImmutableList.of(openTicket), 0, 1, 1));
        when(slackClient.openDmConversation(SlackId.user(assignedTo))).thenReturn(dmResponse);

        BulkReassignRequest request = new BulkReassignRequest(
            List.of(ticket1),
            assignedTo
        );

        // when
        BulkReassignResultUI result = service.bulkReassign(request);

        // then
        assertThat(result.successCount()).isEqualTo(1);
        verify(slackClient).openDmConversation(SlackId.user(assignedTo));
        verify(slackClient, never()).postMessage(any());
    }

    @Test
    void shouldNotSendNotificationWhenDmConversationReturnsNullChannel() {
        // given
        TicketId ticket1 = new TicketId(1);
        String assignedTo = "U12345";

        Ticket openTicket = createTicket(ticket1, TicketStatus.opened);

        ConversationsOpenResponse dmResponse = 
            mock(ConversationsOpenResponse.class);
        when(dmResponse.isOk()).thenReturn(true);
        when(dmResponse.getChannel()).thenReturn(null);

        when(assignmentProps.enabled()).thenReturn(true);
        when(ticketRepository.listTickets(ArgumentMatchers.any(TicketsQuery.class)))
            .thenReturn(new Page<>(ImmutableList.of(openTicket), 0, 1, 1));
        when(slackClient.openDmConversation(SlackId.user(assignedTo))).thenReturn(dmResponse);

        BulkReassignRequest request = new BulkReassignRequest(
            List.of(ticket1),
            assignedTo
        );

        // when
        BulkReassignResultUI result = service.bulkReassign(request);

        // then
        assertThat(result.successCount()).isEqualTo(1);
        verify(slackClient).openDmConversation(SlackId.user(assignedTo));
        verify(slackClient, never()).postMessage(any());
    }

    @Test
    void shouldHandleSlackExceptionWhenPostingMessage() {
        // given
        TicketId ticket1 = new TicketId(1);
        String assignedTo = "U12345";

        Ticket openTicket = createTicket(ticket1, TicketStatus.opened);

        ConversationsOpenResponse dmResponse = 
            mock(ConversationsOpenResponse.class);
        Conversation channel = mock(Conversation.class);
        when(channel.getId()).thenReturn("D12345");
        when(dmResponse.isOk()).thenReturn(true);
        when(dmResponse.getChannel()).thenReturn(channel);

        when(assignmentProps.enabled()).thenReturn(true);
        when(ticketRepository.listTickets(ArgumentMatchers.any(TicketsQuery.class)))
            .thenReturn(new Page<>(ImmutableList.of(openTicket), 0, 1, 1));
        when(slackClient.openDmConversation(SlackId.user(assignedTo))).thenReturn(dmResponse);
        when(slackClient.getPermalink(ArgumentMatchers.any(SlackGetMessageByTsRequest.class)))
            .thenReturn("https://slack.com/permalink1");
        when(slackClient.postMessage(ArgumentMatchers.any(SlackPostMessageRequest.class)))
            .thenThrow(new SlackException(new RuntimeException("Failed to post message")));

        BulkReassignRequest request = new BulkReassignRequest(
            List.of(ticket1),
            assignedTo
        );

        // when
        BulkReassignResultUI result = service.bulkReassign(request);

        // then
        assertThat(result.successCount()).isEqualTo(1);
        verify(slackClient).openDmConversation(SlackId.user(assignedTo));
        verify(slackClient).postMessage(ArgumentMatchers.any(SlackPostMessageRequest.class));
        // Should not throw exception, just log warning
    }

    @Test
    void shouldIncludePermalinksInNotificationMessage() {
        // given
        TicketId ticket1 = new TicketId(1);
        TicketId ticket2 = new TicketId(2);
        String assignedTo = "U12345";

        Ticket openTicket1 = createTicket(ticket1, TicketStatus.opened);
        Ticket openTicket2 = Ticket.builder()
            .id(ticket2)
            .channelId("C456")
            .queryTs(MessageTs.of("789.012"))
            .createdMessageTs(MessageTs.of("789.013"))
            .status(TicketStatus.opened)
            .impact("medium")
            .tags(ImmutableList.of())
            .lastInteractedAt(Instant.now())
            .statusLog(ImmutableList.of(new Ticket.StatusLog(TicketStatus.opened, Instant.now())))
            .build();

        ConversationsOpenResponse dmResponse = 
            mock(ConversationsOpenResponse.class);
        Conversation channel = mock(Conversation.class);
        when(channel.getId()).thenReturn("D12345");
        when(dmResponse.isOk()).thenReturn(true);
        when(dmResponse.getChannel()).thenReturn(channel);

        when(assignmentProps.enabled()).thenReturn(true);
        when(ticketRepository.listTickets(ArgumentMatchers.any(TicketsQuery.class)))
            .thenReturn(new Page<>(ImmutableList.of(openTicket1, openTicket2), 0, 1, 2));
        when(slackClient.openDmConversation(SlackId.user(assignedTo))).thenReturn(dmResponse);
        when(slackClient.getPermalink(ArgumentMatchers.any(SlackGetMessageByTsRequest.class)))
            .thenReturn("https://slack.com/permalink1", "https://slack.com/permalink2");

        BulkReassignRequest request = new BulkReassignRequest(
            List.of(ticket1, ticket2),
            assignedTo
        );

        // when
        service.bulkReassign(request);

        // then
        ArgumentCaptor<SlackGetMessageByTsRequest> permalinkCaptor = 
            ArgumentCaptor.forClass(SlackGetMessageByTsRequest.class);
        verify(slackClient, times(2)).getPermalink(permalinkCaptor.capture());
        
        List<SlackGetMessageByTsRequest> permalinkRequests = permalinkCaptor.getAllValues();
        assertThat(permalinkRequests.get(0).channelId()).isEqualTo("C123");
        assertThat(permalinkRequests.get(0).ts().ts()).isEqualTo("123.456");
        assertThat(permalinkRequests.get(1).channelId()).isEqualTo("C456");
        assertThat(permalinkRequests.get(1).ts().ts()).isEqualTo("789.012");
        
        ArgumentCaptor<SlackPostMessageRequest> messageCaptor = ArgumentCaptor.forClass(SlackPostMessageRequest.class);
        verify(slackClient).postMessage(messageCaptor.capture());
        String messageText = messageCaptor.getValue().message().getText();
        assertThat(messageText).contains("https://slack.com/permalink1");
        assertThat(messageText).contains("https://slack.com/permalink2");
    }

    private Ticket createTicket(TicketId ticketId, TicketStatus status) {
        return Ticket.builder()
            .id(ticketId)
            .channelId("C123")
            .queryTs(MessageTs.of("123.456"))
            .createdMessageTs(MessageTs.of("123.457"))
            .status(status)
            .impact("medium")
            .tags(ImmutableList.of())
            .lastInteractedAt(Instant.now())
            .statusLog(ImmutableList.of(new Ticket.StatusLog(status, Instant.now())))
            .build();
    }
}

