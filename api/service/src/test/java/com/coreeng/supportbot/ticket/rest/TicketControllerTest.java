package com.coreeng.supportbot.ticket.rest;

import com.coreeng.supportbot.ticket.*;
import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketControllerTest {

    @Mock
    private TicketUpdateService ticketUpdateService;

    @Mock
    private TicketQueryService queryService;

    @Mock
    private TicketUIMapper mapper;

    private TicketController controller;

    private TicketId ticketId;

    @BeforeEach
    void setUp() {
        controller = new TicketController(queryService, ticketUpdateService, mapper);
        ticketId = new TicketId(123L);
    }

    @Test
    void shouldReturnOkWhenUpdateSucceeds() {
        // given
        TicketUpdateRequest request = new TicketUpdateRequest(
            TicketStatus.closed,
            "core-support",
            ImmutableList.of("bug", "urgent"),
            "production-blocking",
            null
        );

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
        TicketUpdateRequest request = new TicketUpdateRequest(
            null,
            "core-support",
            ImmutableList.of("bug"),
            "production-blocking",
            null
        );

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
            TicketStatus.closed,
            "core-support",
            ImmutableList.of("bug"),
            "production-blocking",
            null
        );

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
}
