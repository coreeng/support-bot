package com.coreeng.supportbot.ticket.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.coreeng.supportbot.slack.MessageRef;
import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.ticket.DetailedTicket;
import com.coreeng.supportbot.ticket.TicketProcessingService;
import com.coreeng.supportbot.ticket.TicketQueryService;
import com.coreeng.supportbot.ticket.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class TicketTestControllerTest {
    @Mock
    private TicketRepository repository;

    @Mock
    private TicketQueryService queryService;

    @Mock
    private TicketUIMapper mapper;

    @Mock
    private TicketProcessingService ticketProcessingService;

    private TicketTestController controller;

    @BeforeEach
    void setUp() {
        controller = new TicketTestController(repository, queryService, mapper, ticketProcessingService);
    }

    @Test
    void findTicketByQueryReturnsNotFoundWhenTicketDoesNotExist() {
        MessageRef queryRef = new MessageRef(MessageTs.of("111.222"), "C123");
        when(queryService.findDetailedByQueryRef(queryRef)).thenReturn(null);

        ResponseEntity<TicketUI> response = controller.findTicketByQuery("C123", "111.222");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNull();
    }

    @Test
    void findTicketByQueryDelegatesToMapperWhenTicketExists() {
        MessageRef queryRef = new MessageRef(MessageTs.of("111.222"), "C123");
        DetailedTicket detailedTicket = mock(DetailedTicket.class);
        TicketUI mapped = mock(TicketUI.class);

        when(queryService.findDetailedByQueryRef(queryRef)).thenReturn(detailedTicket);
        when(mapper.mapToUI(detailedTicket)).thenReturn(mapped);

        ResponseEntity<TicketUI> response = controller.findTicketByQuery("C123", "111.222");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(mapped);
        verify(mapper).mapToUI(detailedTicket);
    }
}
