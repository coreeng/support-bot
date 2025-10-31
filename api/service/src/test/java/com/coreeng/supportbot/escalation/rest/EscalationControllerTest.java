package com.coreeng.supportbot.escalation.rest;

import com.coreeng.supportbot.escalation.*;
import com.coreeng.supportbot.ticket.Ticket;
import com.coreeng.supportbot.ticket.TicketId;
import com.coreeng.supportbot.ticket.TicketQueryService;
import com.coreeng.supportbot.ticket.TicketsQuery;
import com.coreeng.supportbot.util.Page;
import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EscalationControllerTest {

    private static final EscalationId escalationId = new EscalationId(1);
    private static final TicketId ticketId = new TicketId(1);

    @Mock private EscalationQueryService escalationQueryService;
    @Mock private TicketQueryService ticketQueryService;
    @Mock private EscalationUIMapper mapper;

    @InjectMocks private EscalationController controller;

    @Test
    void shouldReturnMappedEscalationsWithTickets() {
        // given
        Escalation escalation = mock(Escalation.class);
        when(escalation.ticketId()).thenReturn(ticketId);

        Ticket ticket = mock(Ticket.class);
        when(ticket.id()).thenReturn(ticketId);
        when(ticket.team()).thenReturn("TeamA");

        EscalationUI mapped = EscalationUI.builder().id(escalationId).build();
        when(mapper.mapToUI(escalation)).thenReturn(mapped);

        when(escalationQueryService.findByQuery(any()))
                .thenReturn(new Page<>(ImmutableList.of(escalation), 0, 1, 1));
        when(ticketQueryService.findByQuery(any()))
                .thenReturn(new Page<>(ImmutableList.of(ticket), 0, 1, 1));

        // when
        Page<EscalationUI> result = controller.list(
                0L, 10L, List.of(escalationId), ticketId,
                LocalDate.now().minusDays(1), LocalDate.now(),
                EscalationStatus.opened, "TeamA"
        );

        // then
        assertThat(result.content()).hasSize(1);
        EscalationUI ui = result.content().get(0);
        assertThat(ui.id()).isEqualTo(escalationId);
        assertThat(ui.escalatingTeam()).isEqualTo("TeamA");

        verify(escalationQueryService).findByQuery(any(EscalationQuery.class));
        verify(ticketQueryService).findByQuery(any(TicketsQuery.class));
        verify(mapper).mapToUI(escalation);
        verifyNoMoreInteractions(escalationQueryService, ticketQueryService, mapper);
    }

    @Test
    void shouldHandleEmptyEscalations() {
        // given
        when(escalationQueryService.findByQuery(any()))
                .thenReturn(new Page<>(ImmutableList.of(), 0, 1, 0));
        when(ticketQueryService.findByQuery(any()))
                .thenReturn(new Page<>(ImmutableList.of(), 0, 1, 0));

        // when
        Page<EscalationUI> result = controller.list(0L, 10L, List.of(), null, null, null, null, null);

        // then
        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();

        verify(escalationQueryService).findByQuery(any());
        verify(ticketQueryService).findByQuery(any());
        verifyNoMoreInteractions(escalationQueryService, ticketQueryService, mapper);
    }

    @Test
    void shouldPassCorrectQueryToEscalationService() {
        // given
        when(escalationQueryService.findByQuery(any()))
                .thenReturn(new Page<>(ImmutableList.of(), 2, 1, 0));
        when(ticketQueryService.findByQuery(any()))
                .thenReturn(new Page<>(ImmutableList.of(), 0, 1, 0));

        // when
        controller.list(
                2L, 5L,
                List.of(new EscalationId(1), new EscalationId(2)),
                null,
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 12, 31),
                EscalationStatus.resolved,
                "Ops"
        );

        // then
        ArgumentCaptor<EscalationQuery> captor = ArgumentCaptor.forClass(EscalationQuery.class);
        verify(escalationQueryService).findByQuery(captor.capture());

        EscalationQuery query = captor.getValue();
        assertThat(query.page()).isEqualTo(2);
        assertThat(query.pageSize()).isEqualTo(5);
        assertThat(query.dateFrom()).isEqualTo(LocalDate.of(2024, 1, 1));
        assertThat(query.dateTo()).isEqualTo(LocalDate.of(2024, 12, 31));
        assertThat(query.status()).isEqualTo(EscalationStatus.resolved);
        assertThat(query.team()).isEqualTo("Ops");

        verify(ticketQueryService).findByQuery(any());
        verifyNoMoreInteractions(escalationQueryService, ticketQueryService, mapper);
    }
}
