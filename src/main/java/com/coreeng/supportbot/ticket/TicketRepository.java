package com.coreeng.supportbot.ticket;

import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.util.Page;

import javax.annotation.Nullable;

public interface TicketRepository {
    boolean createQueryIfNotExists(MessageTs messageTs);
    boolean queryExists(MessageTs messageTs);

    Ticket createTicketIfNotExists(Ticket ticket);
    Ticket updateTicket(Ticket ticket);

    @Nullable Ticket findTicketById(TicketId ticketId);
    @Nullable Ticket findTicketByQuery(MessageTs messageTs);
    @Nullable DetailedTicket findDetailedById(TicketId id);

    Ticket insertStatusLog(Ticket ticket);
    Page<Ticket> findTickets(TicketsQuery query);

    Page<DetailedTicket> findDetailedTickets(TicketsQuery query);
}
