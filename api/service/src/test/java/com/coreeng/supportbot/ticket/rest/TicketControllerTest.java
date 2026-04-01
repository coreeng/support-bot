package com.coreeng.supportbot.ticket.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.ticket.*;
import com.coreeng.supportbot.util.Page;
import com.google.common.collect.ImmutableList;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class TicketControllerTest {

    @Mock
    private TicketUpdateService ticketUpdateService;

    @Mock
    private TicketQueryService queryService;

    @Mock
    private TicketUIMapper mapper;

    @Mock
    private TicketTeamSuggestionsService teamSuggestionsService;

    private TicketController controller;

    private TicketId ticketId;

    @BeforeEach
    void setUp() {
        controller = new TicketController(queryService, ticketUpdateService, mapper, teamSuggestionsService);
        ticketId = new TicketId(123L);
    }

    @Test
    void shouldReturnOkWhenUpdateSucceeds() {
        // given
        TicketUpdateRequest request = new TicketUpdateRequest(
                TicketStatus.closed, "core-support", ImmutableList.of("bug", "urgent"), "production-blocking", null);

        TicketUI mockTicketUI = mock(TicketUI.class);
        when(ticketUpdateService.update(ticketId, request)).thenReturn(mockTicketUI);

        // when
        ResponseEntity<?> response = controller.updateTicket(ticketId, request);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(mockTicketUI);
        verify(ticketUpdateService).update(ticketId, request);
    }

    @Test
    void shouldReturnBadRequestWhenValidationFails() {
        // given
        TicketUpdateRequest request =
                new TicketUpdateRequest(null, "core-support", ImmutableList.of("bug"), "production-blocking", null);

        when(ticketUpdateService.update(ticketId, request))
                .thenThrow(new IllegalArgumentException("status is required"));

        // when
        ResponseEntity<?> response = controller.updateTicket(ticketId, request);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo("status is required");
        verify(ticketUpdateService).update(ticketId, request);
    }

    @Test
    void shouldReturnBadRequestWhenSubmitFails() {
        // given
        TicketUpdateRequest request = new TicketUpdateRequest(
                TicketStatus.closed, "core-support", ImmutableList.of("bug"), "production-blocking", null);

        when(ticketUpdateService.update(ticketId, request))
                .thenThrow(new IllegalStateException("Update failed: unresolved escalations"));

        // when
        ResponseEntity<?> response = controller.updateTicket(ticketId, request);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isInstanceOf(String.class);
        assertThat((String) response.getBody()).contains("Update failed");
        verify(ticketUpdateService).update(ticketId, request);
    }

    @Test
    void listDelegatesToMapperForBatchMapping() {
        // given
        DetailedTicket firstDetailedTicket = detailedTicket(new TicketId(1L));
        DetailedTicket secondDetailedTicket = detailedTicket(new TicketId(2L));
        ImmutableList<DetailedTicket> detailedTickets = ImmutableList.of(firstDetailedTicket, secondDetailedTicket);
        Page<DetailedTicket> detailedTicketsPage = new Page<>(detailedTickets, 0, 1, 2);
        TicketUI firstTicketUi = mock(TicketUI.class);
        TicketUI secondTicketUi = mock(TicketUI.class);
        ImmutableList<TicketUI> mappedTickets = ImmutableList.of(firstTicketUi, secondTicketUi);

        when(queryService.findDetailedTicketByQuery(any())).thenReturn(detailedTicketsPage);
        when(mapper.mapToUIList(detailedTickets)).thenReturn(mappedTickets);

        // when
        ResponseEntity<Page<TicketUI>> response = controller.list(
                0,
                10,
                List.of(),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                TicketStatus.opened,
                false,
                List.of(),
                List.of(),
                "");

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().content()).containsExactly(firstTicketUi, secondTicketUi);
        verify(mapper).mapToUIList(detailedTickets);
    }

    @Test
    void findByIdDelegatesToMapper() {
        // given
        DetailedTicket detailedTicket = detailedTicket(ticketId);
        TicketUI mappedTicketUI = mock(TicketUI.class);

        when(queryService.findDetailedById(ticketId)).thenReturn(detailedTicket);
        when(queryService.fetchQueryText(detailedTicket.ticket())).thenReturn("Original message");
        when(mapper.mapToUI(detailedTicket, "Original message")).thenReturn(mappedTicketUI);

        // when
        ResponseEntity<TicketUI> response = controller.findById(ticketId);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(mappedTicketUI);
        verify(mapper).mapToUI(detailedTicket, "Original message");
    }

    @Test
    void getTeamSuggestions_returnsGroupedTeams() {
        // given
        TicketTeamsSuggestion suggestion =
                new TicketTeamsSuggestion(ImmutableList.of("AuthorTeam"), ImmutableList.of("OtherTeam"));
        when(teamSuggestionsService.getTeamSuggestionsForTicket(ticketId)).thenReturn(Optional.of(suggestion));

        // when
        ResponseEntity<TicketTeamSuggestionsUI> response = controller.getTeamSuggestions(ticketId);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        TicketTeamSuggestionsUI body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.suggestedTeams()).containsExactly("AuthorTeam");
        assertThat(body.otherTeams()).containsExactly("OtherTeam");
    }

    @Test
    void getTeamSuggestions_ticketNotFound_returns404() {
        // given
        when(teamSuggestionsService.getTeamSuggestionsForTicket(ticketId)).thenReturn(Optional.empty());

        // when
        ResponseEntity<TicketTeamSuggestionsUI> response = controller.getTeamSuggestions(ticketId);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private static DetailedTicket detailedTicket(TicketId id) {
        Ticket ticket = Ticket.builder()
                .id(id)
                .channelId("C123")
                .queryTs(MessageTs.of("123.456"))
                .createdMessageTs(MessageTs.of("123.457"))
                .status(TicketStatus.opened)
                .team(null)
                .impact("production-blocking")
                .tags(ImmutableList.of())
                .lastInteractedAt(Instant.now())
                .statusLog(ImmutableList.of(new Ticket.StatusLog(TicketStatus.opened, Instant.now())))
                .build();
        return new DetailedTicket(ticket, ImmutableList.of());
    }
}
