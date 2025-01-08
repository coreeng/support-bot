package com.coreeng.supportbot.ticket;

import com.coreeng.supportbot.slack.MessageTs;

import javax.annotation.Nullable;

public interface TicketRepository {
    boolean createQueryIfNotExists(MessageTs messageTs);
    boolean queryExists(MessageTs messageTs);

    Ticket createTicketIfNotExists(Ticket ticket);
    Ticket updateTicket(Ticket ticket);

    @Nullable Ticket findTicketById(TicketId ticketId);
    @Nullable Ticket findTicketByQuery(MessageTs messageTs);

    Ticket insertStatusLog(Ticket ticket);
}
