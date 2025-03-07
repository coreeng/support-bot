package com.coreeng.supportbot.ticket;

import com.coreeng.supportbot.slack.MessageRef;
import com.coreeng.supportbot.slack.MessageTs;
import com.google.common.collect.ImmutableList;
import lombok.Builder;
import lombok.Getter;
import lombok.With;

import javax.annotation.Nullable;
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
    private String team;
    @Builder.Default
    private ImmutableList<StatusLog> statusLog = ImmutableList.of();
    @Builder.Default
    ImmutableList<String> tags = ImmutableList.of();
    @Nullable
    private String impact;
    private Instant lastInteractedAt;

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
