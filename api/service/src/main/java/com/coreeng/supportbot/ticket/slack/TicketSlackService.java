package com.coreeng.supportbot.ticket.slack;

import com.coreeng.supportbot.slack.MessageRef;
import com.coreeng.supportbot.ticket.TicketCreatedMessage;
import com.coreeng.supportbot.ticket.TicketId;

public interface TicketSlackService {
    void markPostTracked(MessageRef threadRef);
    void markTicketClosed(MessageRef threadRef);
    void unmarkTicketClosed(MessageRef threadRef);
    void markTicketEscalated(MessageRef threadRef);

    MessageRef postTicketForm(MessageRef threadRef, TicketCreatedMessage message);
    void editTicketForm(MessageRef threadRef, TicketCreatedMessage message);
    void warnStaleness(MessageRef queryRef);
    void postRatingRequest(MessageRef queryRef, TicketId ticketId, String userId);

    boolean isThreadReply(MessageRef messageRef);
}
