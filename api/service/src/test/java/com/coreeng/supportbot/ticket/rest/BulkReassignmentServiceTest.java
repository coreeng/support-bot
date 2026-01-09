package com.coreeng.supportbot.ticket.rest;

import com.coreeng.supportbot.config.TicketAssignmentProps;
import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.ticket.Ticket;
import com.coreeng.supportbot.ticket.TicketId;
import com.coreeng.supportbot.ticket.TicketRepository;
import com.coreeng.supportbot.ticket.TicketStatus;
import com.coreeng.supportbot.ticket.TicketsQuery;
import com.coreeng.supportbot.util.Page;
import com.google.common.collect.ImmutableList;
import org.jooq.exception.DataAccessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    @InjectMocks
    private BulkReassignmentService service;

    @BeforeEach
    void setUp() {
        service = new BulkReassignmentService(assignmentProps, ticketRepository);
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
    void shouldSkipClosedAndStaleTickets() {
        // given
        TicketId ticket1 = new TicketId(1);
        TicketId ticket2 = new TicketId(2);
        TicketId ticket3 = new TicketId(3);
        TicketId ticket4 = new TicketId(4);
        String assignedTo = "U12345";

        Ticket openTicket = createTicket(ticket1, TicketStatus.opened);
        Ticket closedTicket = createTicket(ticket2, TicketStatus.closed);
        Ticket staleTicket = createTicket(ticket3, TicketStatus.stale);
        Ticket anotherOpenTicket = createTicket(ticket4, TicketStatus.opened);

        when(assignmentProps.enabled()).thenReturn(true);
        when(ticketRepository.listTickets(ArgumentMatchers.any(TicketsQuery.class)))
            .thenReturn(new Page<>(ImmutableList.of(openTicket, closedTicket, staleTicket, anotherOpenTicket), 0, 1, 4));

        BulkReassignRequest request = new BulkReassignRequest(
            List.of(ticket1, ticket2, ticket3, ticket4),
            assignedTo
        );

        // when
        BulkReassignResultUI result = service.bulkReassign(request);

        // then
        assertThat(result.successCount()).isEqualTo(2);
        assertThat(result.successfulTicketIds()).containsExactly(ticket1, ticket4);
        assertThat(result.skippedCount()).isEqualTo(2);
        assertThat(result.skippedTicketIds()).containsExactly(ticket2, ticket3);
        assertThat(result.message()).isEqualTo("2 of 4 tickets successfully reassigned, 2 skipped");

        verify(assignmentProps).enabled();
        verify(ticketRepository).listTickets(ArgumentMatchers.any(TicketsQuery.class));
        verify(ticketRepository).assign(ticket1, assignedTo);
        verify(ticketRepository).assign(ticket4, assignedTo);
        verify(ticketRepository, never()).assign(ticket2, assignedTo);
        verify(ticketRepository, never()).assign(ticket3, assignedTo);
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

