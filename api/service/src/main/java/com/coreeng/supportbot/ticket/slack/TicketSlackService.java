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

    /**
     * Adds the bot's {@code name} reaction to the message, tolerating it already being there. Any other Slack
     * error propagates. A mocked message is skipped.
     */
    void addReaction(MessageRef messageRef, String name);

    /**
     * Removes the bot's {@code name} reaction from the message, tolerating it already being absent. Any other
     * Slack error propagates. A mocked message is skipped.
     */
    void removeReaction(MessageRef messageRef, String name);

    MessageRef postTicketForm(MessageRef threadRef, TicketCreatedMessage message);

    void editTicketForm(MessageRef threadRef, TicketCreatedMessage message);

    void warnStaleness(MessageRef queryRef, StalenessTagTarget target);

    @Nullable List<String> getReactionUserIds(MessageRef queryRef, String reactionName);

    void postRatingRequest(MessageRef queryRef, TicketId ticketId, String userId);

    boolean isThreadReply(MessageRef messageRef);
}
