package com.coreeng.supportbot.ticket;

import com.coreeng.supportbot.slack.MessageRef;
import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.slack.SlackId;
import com.google.common.collect.ImmutableList;
import lombok.Builder;
import lombok.Getter;
import lombok.With;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

@Getter
@Builder(toBuilder = true)
public class Ticket {
    @With
    @Nullable
    private TicketId id;
    private String channelId;
    private MessageTs queryTs;
    private MessageTs createdMessageTs;
    private TicketStatus status;
    @Nullable
    private TicketTeam team;
    @Builder.Default
    private ImmutableList<StatusLog> statusLog = ImmutableList.of();
    @Builder.Default
    private ImmutableList<String> tags = ImmutableList.of();
    @Nullable
    private String impact;
    private Instant lastInteractedAt;
    @Builder.Default
    private boolean ratingSubmitted = false;
    private SlackId.User assignedTo;

    public static Ticket createNew(MessageTs queryTs, String channelID) {
        return Ticket.builder()
            .channelId(channelID)
            .queryTs(queryTs)
            .status(TicketStatus.opened)
            .statusLog(ImmutableList.of(new StatusLog(
                TicketStatus.opened,
                Instant.now()
            )))
            .lastInteractedAt(Instant.now())
            .build();
    }

    public MessageRef queryRef() {
        return new MessageRef(queryTs, channelId);
    }

    public record StatusLog(
        TicketStatus status,
        Instant date
    ) {
    }
}
