package com.coreeng.supportbot.ticket;

import com.coreeng.supportbot.EnumerationValue;
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
    @Builder.Default
    ImmutableList<StatusLog> statusHistory = ImmutableList.of();
    @Builder.Default
    ImmutableList<EnumerationValue> tags = ImmutableList.of();
    @Nullable EnumerationValue impact;

    public static Ticket createNew(MessageTs queryTs, String channelID) {
        return Ticket.builder()
            .channelId(channelID)
            .queryTs(queryTs)
            .status(TicketStatus.unresolved)
            .statusHistory(ImmutableList.of(new StatusLog(
                TicketStatus.unresolved,
                Instant.now()
            )))
            .build();
    }

    record StatusLog(
        TicketStatus status,
        Instant timestamp
    ) {
    }
}
