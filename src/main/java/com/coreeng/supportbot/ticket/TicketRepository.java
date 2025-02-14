package com.coreeng.supportbot.ticket;

import com.coreeng.supportbot.slack.MessageRef;
import com.coreeng.supportbot.util.Page;
import com.google.common.collect.ImmutableList;

import javax.annotation.Nullable;
import java.time.Duration;
import java.time.Instant;

public interface TicketRepository {
    void createQueryIfNotExists(MessageRef queryRef);
    boolean queryExists(MessageRef queryRef);

    Ticket createTicketIfNotExists(Ticket ticket);
    Ticket updateTicket(Ticket ticket);
    boolean touchTicketById(TicketId id, Instant timestamp);

    @Nullable Ticket findTicketById(TicketId ticketId);
    @Nullable Ticket findTicketByQuery(MessageRef queryRef);
    @Nullable DetailedTicket findDetailedById(TicketId id);

    Ticket insertStatusLog(Ticket ticket, Instant at);

    Page<Ticket> listTickets(TicketsQuery query);
    Page<DetailedTicket> listDetailedTickets(TicketsQuery query);

    ImmutableList<TicketId> listStaleTicketIds(Instant checkAt, Duration timeToStale);
    ImmutableList<TicketId> listStaleTicketIdsToRemindOf(Instant checkAt, Duration reminderInterval);
}
