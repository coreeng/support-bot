package com.coreeng.supportbot.rating;

import com.coreeng.supportbot.escalation.EscalationQueryService;
import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.ticket.Ticket;
import com.coreeng.supportbot.ticket.TicketId;
import com.coreeng.supportbot.ticket.TicketInMemoryRepository;
import com.coreeng.supportbot.ticket.TicketStatus;
import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RatingServiceSaveTest {

    private RatingInMemoryRepository ratingRepository;
    private TicketInMemoryRepository ticketRepository;
    @Mock
    private EscalationQueryService escalationQueryService;

    private RatingService service;

    @BeforeEach
    void setUp() {
        ratingRepository = new RatingInMemoryRepository();
        ticketRepository = new TicketInMemoryRepository(escalationQueryService, ZoneId.of("UTC"));
        service = new RatingService(ratingRepository, ticketRepository, escalationQueryService);
    }

    @Test
    void returnsNull_whenTicketAlreadyRated() {
        Ticket ticket = Ticket.builder()
            .channelId("C123")
            .queryTs(MessageTs.of("111.222"))
            .status(TicketStatus.closed)
            .build();
        Ticket created = ticketRepository.createTicketIfNotExists(ticket);
        ticketRepository.markTicketAsRated(created.id());

        Rating result = service.save(created.id(), 4);

        assertThat(result).isNull();
        assertThat(ratingRepository.size()).isEqualTo(0);
    }

    @Test
    void returnsNull_whenTicketNotFound() {
        TicketId missing = new TicketId(99_999);

        Rating result = service.save(missing, 5);

        assertThat(result).isNull();
        assertThat(ratingRepository.size()).isEqualTo(0);
    }

    @Test
    void savesRating_andMarksTicketRated_whenNotEscalated() {
        Ticket ticket = Ticket.builder()
            .channelId("C123")
            .queryTs(MessageTs.of("111.222"))
            .status(TicketStatus.closed)
            .impact("production blocking")
            .tags(ImmutableList.of("ingress", "api"))
            .build();
        Ticket created = ticketRepository.createTicketIfNotExists(ticket);

        when(escalationQueryService.existsByTicketId(created.id())).thenReturn(false);

        Rating saved = service.save(created.id(), 4);

        assertThat(saved).isNotNull();
        assertThat(saved.id()).isNotNull();
        assertThat(saved.rating()).isEqualTo(4);
        assertThat(saved.status()).isEqualTo(TicketStatus.closed);
        assertThat(saved.impact()).isEqualTo("production blocking");
        assertThat(saved.tags()).containsExactly("ingress", "api");
        assertThat(saved.isEscalated()).isFalse();
        assertThat(saved.submittedTs()).isNotNull();

        Rating persisted = ratingRepository.findById(saved.id());
        assertThat(persisted).isNotNull();
        assertThat(persisted.rating()).isEqualTo(4);
        assertThat(persisted.status()).isEqualTo(TicketStatus.closed);
        assertThat(persisted.impact()).isEqualTo("production blocking");
        assertThat(persisted.tags()).containsExactly("ingress", "api");
        assertThat(persisted.isEscalated()).isFalse();

        assertThat(ticketRepository.isTicketRated(created.id())).isTrue();
    }

    @Test
    void savesRating_andMarksTicketRated_whenEscalated() {
        Ticket ticket = Ticket.builder()
            .channelId("C123")
            .queryTs(MessageTs.of("333.444"))
            .status(TicketStatus.closed)
            .impact("bau")
            .tags(ImmutableList.of("ui"))
            .build();
        Ticket created = ticketRepository.createTicketIfNotExists(ticket);

        when(escalationQueryService.existsByTicketId(created.id())).thenReturn(true);

        Rating saved = service.save(created.id(), 2);

        assertThat(saved).isNotNull();
        assertThat(saved.id()).isNotNull();
        assertThat(saved.rating()).isEqualTo(2);
        assertThat(saved.status()).isEqualTo(TicketStatus.closed);
        assertThat(saved.impact()).isEqualTo("bau");
        assertThat(saved.tags()).containsExactly("ui");
        assertThat(saved.isEscalated()).isTrue();

        Rating persisted = ratingRepository.findById(saved.id());
        assertThat(persisted).isNotNull();
        assertThat(persisted.isEscalated()).isTrue();

        assertThat(ticketRepository.isTicketRated(created.id())).isTrue();
    }
}
