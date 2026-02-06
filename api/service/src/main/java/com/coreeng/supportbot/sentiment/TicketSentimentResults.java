package com.coreeng.supportbot.sentiment;

import com.coreeng.supportbot.sentiment.client.Sentiment;
import com.coreeng.supportbot.ticket.TicketId;
import lombok.Builder;
import lombok.Getter;

import org.jspecify.annotations.Nullable;

@Getter
@Builder(toBuilder = true)
public class TicketSentimentResults {
    private TicketId ticketId;
    @Nullable
    private Sentiment authorSentiment;
    @Nullable
    private Sentiment supportSentiment;
    @Nullable
    private Sentiment othersSentiment;
}
