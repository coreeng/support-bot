package com.coreeng.supportbot.testkit;

import lombok.Builder;
import lombok.Getter;

import static com.coreeng.supportbot.testkit.TicketTestKit.messageToBlocksJson;

@Builder
@Getter
public class TicketByIdQuery {
    private long ticketId;

    // Used for stubbing
    private String channelId;
    private MessageTs queryTs;
    private String queryText;

    public static TicketByIdQuery fromTicket(Ticket ticket) {
        return TicketByIdQuery.builder()
            .ticketId(ticket.id())
            .channelId(ticket.channelId())
            .queryTs(ticket.queryTs())
            .queryText(ticket.queryText())
            .build();
    }

    public static TicketByIdQuery fromTicketMessage(TicketMessage ticketMessage, String queryText) {
        return TicketByIdQuery.builder()
            .ticketId(ticketMessage.ticketId())
            .channelId(ticketMessage.channelId())
            .queryTs(ticketMessage.queryTs())
            .queryText(queryText)
            .build();
    }

    public String queryBlocksJson() {
        return messageToBlocksJson(queryText);
    }
}
