package com.coreeng.supportbot.ticket;

import com.coreeng.supportbot.slack.MessageRef;
import com.coreeng.supportbot.util.Page;

import javax.annotation.Nullable;
import java.time.Instant;

public interface TicketRepository {
    void createQueryIfNotExists(MessageRef queryRef);
    boolean queryExists(MessageRef queryRef);

    Ticket createTicketIfNotExists(Ticket ticket);
    Ticket updateTicket(Ticket ticket);

    @Nullable Ticket findTicketById(TicketId ticketId);
    @Nullable Ticket findTicketByQuery(MessageRef queryRef);
    @Nullable DetailedTicket findDetailedById(TicketId id);

    Ticket insertStatusLog(Ticket ticket, Instant at);
    Page<Ticket> findTickets(TicketsQuery query);

    Page<DetailedTicket> findDetailedTickets(TicketsQuery query);
}
