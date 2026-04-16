package com.coreeng.supportbot.rating;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.coreeng.supportbot.escalation.EscalationQueryService;
import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.ticket.Ticket;
import com.coreeng.supportbot.ticket.TicketId;
import com.coreeng.supportbot.ticket.TicketInMemoryRepository;
import com.coreeng.supportbot.ticket.TicketStatus;
import com.google.common.collect.ImmutableList;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    void rejects_whenTicketAlreadyRated() {
        Ticket ticket = Ticket.builder()
                .channelId("C123")
                .queryTs(MessageTs.of("111.222"))
                .status(TicketStatus.closed)
                .build();
        Ticket created = ticketRepository.createTicketIfNotExists(ticket);
        TicketId createdId = requireNonNull(created.id());
        assertThat(ticketRepository.tryMarkTicketAsRated(createdId)).isTrue();

        assertThatThrownBy(() -> service.save(createdId, 4))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Ticket has already been rated");
        assertThat(ratingRepository.size()).isEqualTo(0);
    }

    @Test
    void rejects_whenTicketNotFound() {
        TicketId missing = new TicketId(99_999);

        assertThatThrownBy(() -> service.save(missing, 5))
                .isInstanceOf(RatingTicketNotFoundException.class)
                .hasMessage("Ticket not found: ID-99999");
        assertThat(ratingRepository.size()).isEqualTo(0);
    }

    @Test
    void rejects_whenTicketIsNotClosed() {
        Ticket ticket = Ticket.builder()
                .channelId("C123")
                .queryTs(MessageTs.of("555.666"))
                .status(TicketStatus.opened)
                .build();
        Ticket created = ticketRepository.createTicketIfNotExists(ticket);
        TicketId createdId = requireNonNull(created.id());

        assertThatThrownBy(() -> service.save(createdId, 4))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Ticket must be closed before rating can be submitted");
        assertThat(ratingRepository.size()).isEqualTo(0);
    }

    @Test
    void rejects_whenRatingIsBelowRange() {
        TicketId createdId = createClosedTicket("777.888");

        assertThatThrownBy(() -> service.save(createdId, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("rating must be between 1 and 5");
        assertThat(ratingRepository.size()).isEqualTo(0);
    }

    @Test
    void rejects_whenRatingIsAboveRange() {
        TicketId createdId = createClosedTicket("999.000");

        assertThatThrownBy(() -> service.save(createdId, 6))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("rating must be between 1 and 5");
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
        TicketId createdId = requireNonNull(created.id());

        when(escalationQueryService.existsByTicketId(createdId)).thenReturn(false);

        Rating saved = service.save(createdId, 4);

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

        assertThat(requireNonNull(ticketRepository.findTicketById(createdId)).ratingSubmitted())
                .isTrue();
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
        TicketId createdId = requireNonNull(created.id());

        when(escalationQueryService.existsByTicketId(createdId)).thenReturn(true);

        Rating saved = service.save(createdId, 2);

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

        assertThat(requireNonNull(ticketRepository.findTicketById(createdId)).ratingSubmitted())
                .isTrue();
    }

    @Test
    void rejects_secondSubmissionForSameTicket() {
        TicketId createdId = createClosedTicket("444.555");
        when(escalationQueryService.existsByTicketId(createdId)).thenReturn(false);

        Rating first = service.save(createdId, 5);

        assertThat(first).isNotNull();
        assertThatThrownBy(() -> service.save(createdId, 4))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Ticket has already been rated");
        assertThat(ratingRepository.size()).isEqualTo(1);
        assertThat(requireNonNull(ticketRepository.findTicketById(createdId)).ratingSubmitted())
                .isTrue();
    }

    private TicketId createClosedTicket(String queryTs) {
        Ticket created = ticketRepository.createTicketIfNotExists(Ticket.builder()
                .channelId("C123")
                .queryTs(MessageTs.of(queryTs))
                .status(TicketStatus.closed)
                .build());
        return requireNonNull(created.id());
    }
}
