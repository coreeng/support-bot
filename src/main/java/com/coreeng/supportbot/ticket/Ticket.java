package com.coreeng.supportbot.ticket;

import com.coreeng.supportbot.slack.MessageRef;
import com.coreeng.supportbot.slack.MessageTs;
import com.google.common.collect.ImmutableList;
import lombok.Builder;
import lombok.Value;
import lombok.With;

import javax.annotation.Nullable;
import java.time.Instant;

@Value
@Builder(toBuilder = true)
public class Ticket {
    @With
    @Nullable
    TicketId id;
    String channelId;
    MessageTs queryTs;
    MessageTs createdMessageTs;
    TicketStatus status;
    @Nullable
    String team;
    @Builder.Default
    ImmutableList<StatusLog> statusLog = ImmutableList.of();
    @Builder.Default
    ImmutableList<String> tags = ImmutableList.of();
    @Nullable
    String impact;

    public static Ticket createNew(MessageTs queryTs, String channelID) {
        return Ticket.builder()
            .channelId(channelID)
            .queryTs(queryTs)
            .status(TicketStatus.opened)
            .statusLog(ImmutableList.of(new StatusLog(
                TicketStatus.opened,
                Instant.now()
            )))
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
