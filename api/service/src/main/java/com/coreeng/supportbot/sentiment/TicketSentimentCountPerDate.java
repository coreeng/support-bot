package com.coreeng.supportbot.sentiment;

import lombok.Builder;
import lombok.Getter;

import org.jspecify.annotations.Nullable;
import java.time.LocalDate;

@Getter
@Builder(toBuilder = true)
public class TicketSentimentCountPerDate {
    private LocalDate date;
    private SentimentCounts authorSentiments;
    @Nullable
    private SentimentCounts supportSentiments;
    @Nullable
    private SentimentCounts othersSentiments;

    public record SentimentCounts(
        long positives,
        long neutrals,
        long negatives
    ) {}
}
