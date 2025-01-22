package com.coreeng.supportbot.ticket.slack;

import com.coreeng.supportbot.slack.MessageRef;
import com.coreeng.supportbot.ticket.TicketCreatedMessage;

public interface TicketSlackService {
    void markPostTracked(MessageRef threadRef);
    void markTicketClosed(MessageRef threadRef);
    void unmarkTicketClosed(MessageRef threadRef);

    MessageRef postTicketForm(MessageRef threadRef, TicketCreatedMessage message);
    void editTicketForm(MessageRef threadRef, TicketCreatedMessage message);
    void postTicketEscalatedMessage(MessageRef queryRef, MessageRef escalationThreadRef, String slackTeamName);
}
