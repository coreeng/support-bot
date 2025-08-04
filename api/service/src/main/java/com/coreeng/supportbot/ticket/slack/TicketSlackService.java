package com.coreeng.supportbot.ticket.slack;

import com.coreeng.supportbot.slack.MessageRef;
import com.coreeng.supportbot.ticket.TicketCreatedMessage;

public interface TicketSlackService {
    void markPostTracked(MessageRef threadRef);
    void markTicketClosed(MessageRef threadRef);
    void unmarkTicketClosed(MessageRef threadRef);
    void markTicketEscalated(MessageRef threadRef);

    MessageRef postTicketForm(MessageRef threadRef, TicketCreatedMessage message);
    void editTicketForm(MessageRef threadRef, TicketCreatedMessage message);
    void postTicketEscalatedMessage(MessageRef queryRef, MessageRef escalationThreadRef, String slackTeamName);

    void warnStaleness(MessageRef queryRef);
    void requestFeedback(MessageRef threadRef, String ticketId, String userId);
}
