package com.coreeng.supportbot.sentiment;

import com.coreeng.supportbot.sentiment.client.Sentiment;
import com.coreeng.supportbot.ticket.TicketId;
import lombok.Builder;
import lombok.Getter;

import javax.annotation.Nullable;

@Getter
@Builder(toBuilder = true)
public class TicketSentimentResults {
    private TicketId ticketId;
    private Sentiment authorSentiment;
    @Nullable
    private Sentiment supportSentiment;
    @Nullable
    private Sentiment othersSentiment;
}
