package com.coreeng.supportbot.stats;

import com.coreeng.supportbot.sentiment.SentimentRepository;
import com.coreeng.supportbot.sentiment.TicketSentimentCountPerDate;
import com.google.common.collect.ImmutableList;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty("ai.sentiment-analysis.enabled")
public class TicketsSentimentsCountCollector implements StatsCollector<StatsRequest.TicketSentimentCounts> {
    private final SentimentRepository sentimentRepository;

    @Override
    public StatsType getSupportedType() {
        return StatsType.ticketSentimentsCount;
    }

    @Override
    public StatsResult calculateResults(StatsRequest.TicketSentimentCounts request) {
        ImmutableList<TicketSentimentCountPerDate> results =
                sentimentRepository.countBetweenDates(request.from(), request.to());
        return StatsResult.TicketSentimentCounts.builder()
                .request(request)
                .values(results)
                .build();
    }
}
