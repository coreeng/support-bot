package com.coreeng.supportbot.ticket;

import com.coreeng.supportbot.slack.MessageRef;
import com.coreeng.supportbot.util.Page;
import com.google.common.collect.ImmutableList;
import java.time.Duration;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

public interface TicketRepository {
    void createQueryIfNotExists(MessageRef queryRef);

    boolean queryExists(MessageRef queryRef);

    boolean deleteQueryIfNoTicket(MessageRef queryRef);

    Ticket createTicketIfNotExists(Ticket ticket);

    Ticket updateTicket(Ticket ticket);

    boolean touchTicketById(TicketId id, Instant timestamp);

    @Nullable Ticket findTicketById(TicketId ticketId);

    @Nullable Ticket findTicketByQuery(MessageRef queryRef);

    Ticket insertStatusLog(Ticket ticket, Instant at);

    Page<Ticket> listTickets(TicketsQuery query);

    ImmutableList<TicketId> listStaleTicketIds(Instant checkAt, Duration timeToStale);

    ImmutableList<TicketId> listStaleTicketIdsToRemindOf(Instant checkAt, Duration reminderInterval);

    boolean isTicketRated(TicketId ticketId);

    void markTicketAsRated(TicketId ticketId);

    boolean assign(TicketId ticketId, String slackUserId);
}
