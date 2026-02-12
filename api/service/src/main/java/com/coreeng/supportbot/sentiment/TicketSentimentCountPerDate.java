package com.coreeng.supportbot.sentiment;

import java.time.LocalDate;
import lombok.Builder;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

@Getter
@Builder(toBuilder = true)
public class TicketSentimentCountPerDate {
    private LocalDate date;
    private SentimentCounts authorSentiments;

    @Nullable private SentimentCounts supportSentiments;

    @Nullable private SentimentCounts othersSentiments;

    public record SentimentCounts(long positives, long neutrals, long negatives) {}
}
