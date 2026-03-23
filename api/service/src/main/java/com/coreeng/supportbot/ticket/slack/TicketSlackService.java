package com.coreeng.supportbot.ticket.slack;

import com.coreeng.supportbot.slack.MessageRef;
import com.coreeng.supportbot.ticket.StalenessTagTarget;
import com.coreeng.supportbot.ticket.TicketCreatedMessage;
import com.coreeng.supportbot.ticket.TicketId;
import java.util.List;
import org.jspecify.annotations.Nullable;

public interface TicketSlackService {
    void markPostTracked(MessageRef threadRef);

    void markTicketClosed(MessageRef threadRef);

    void unmarkTicketClosed(MessageRef threadRef);

    void markTicketEscalated(MessageRef threadRef);

    MessageRef postTicketForm(MessageRef threadRef, TicketCreatedMessage message);

    void editTicketForm(MessageRef threadRef, TicketCreatedMessage message);

    void warnStaleness(MessageRef queryRef, StalenessTagTarget target);

    @Nullable List<String> getReactionUserIds(MessageRef queryRef, String reactionName);

    void postRatingRequest(MessageRef queryRef, TicketId ticketId, String userId);

    boolean isThreadReply(MessageRef messageRef);
}
