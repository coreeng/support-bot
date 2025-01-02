package com.coreeng.supportbot.ticket;

import com.coreeng.supportbot.slack.MessageTs;
import com.slack.api.bolt.request.builtin.BlockActionRequest;

public record ToggleTicketAction(
    String channelId,
    MessageTs threadTs,
    MessageTs messageTs
) {
    public static ToggleTicketAction fromRaw(BlockActionRequest req) {
        return new ToggleTicketAction(
            req.getPayload().getMessage().getChannel(),
            MessageTs.of(req.getPayload().getMessage().getThreadTs()),
            MessageTs.of(req.getPayload().getMessage().getTs())
        );
    }
}
