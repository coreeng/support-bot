package com.coreeng.supportbot.ticket;

import com.coreeng.supportbot.slack.MessageTs;
import com.slack.api.bolt.request.builtin.BlockActionRequest;

public record TicketSummaryViewQuery(
    String channelId,
    MessageTs queryTs
) {
    public static TicketSummaryViewQuery fromRaw(BlockActionRequest req) {
        return new TicketSummaryViewQuery(
            req.getPayload().getChannel().getId(),
            MessageTs.of(req.getPayload().getMessage().getThreadTs())
        );
    }
}
