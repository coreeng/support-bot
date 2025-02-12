package com.coreeng.supportbot.sentiment;

import com.coreeng.supportbot.sentiment.client.Sentiment;
import com.coreeng.supportbot.ticket.Ticket;
import com.coreeng.supportbot.ticket.TicketId;
import com.coreeng.supportbot.ticket.TicketQueryService;
import com.coreeng.supportbot.ticket.TicketStatus;
import com.coreeng.supportbot.ticket.TicketsQuery;
import com.coreeng.supportbot.util.Page;
import com.google.common.collect.ImmutableList;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.stream.Collectors.groupingBy;

@Repository
@RequiredArgsConstructor
public class SentimentInMemoryRepository implements SentimentRepository {
    private final TicketQueryService ticketQueryService;
    private final ZoneId timezone;

    private final Map<TicketId, TicketSentimentResults> sentiments = new HashMap<>();

    @Override
    public void save(TicketId ticketId, TicketSentimentResults sentiment) {
        sentiments.put(ticketId, sentiment);
    }

    @Override
    public ImmutableList<TicketId> listNotAnalysedClosedTickets() {
        Page<Ticket> closedTickets = ticketQueryService.findByQuery(TicketsQuery.builder()
            .unlimited(true)
            .status(TicketStatus.closed)
            .build());
        return closedTickets.content().stream()
            .map(Ticket::id)
            .filter(tId -> !sentiments.containsKey(tId))
            .collect(toImmutableList());
    }

    @Nullable
    @Override
    public TicketSentimentResults findByTicketId(TicketId ticketId) {
        return sentiments.get(ticketId);
    }

    @Override
    public ImmutableList<TicketSentimentCountPerDate> countBetweenDates(LocalDate from, LocalDate to) {
        Page<Ticket> tickets = ticketQueryService.findByQuery(TicketsQuery.builder()
            .ids(ImmutableList.copyOf(sentiments.keySet()))
            .unlimited(true)
            .dateFrom(from)
            .dateTo(to)
            .build());

        return tickets.content().stream()
            .filter(t -> sentiments.containsKey(t.id()))
            .collect(groupingBy(
                t -> LocalDate.ofInstant(t.statusLog().getLast().date(), timezone)
            ))
            .entrySet().stream()
            .map(e -> countSentiments(e.getKey(), e.getValue()))
            .collect(toImmutableList());
    }

    private TicketSentimentCountPerDate countSentiments(LocalDate date, List<Ticket> tickets) {
        class SentimentCounters {
            long positives;
            long neutrals;
            long negatives;

            void increment(Sentiment sentiment) {
                switch (sentiment.conclusion()) {
                    case positive -> positives++;
                    case neutral -> neutrals++;
                    case negative -> negatives++;
                }
            }

            TicketSentimentCountPerDate.SentimentCounts toModel() {
                return new TicketSentimentCountPerDate.SentimentCounts(positives, neutrals, negatives);
            }
        }

        SentimentCounters authorSentiments = new SentimentCounters();
        SentimentCounters supportSentiments = new SentimentCounters();
        SentimentCounters othersSentiments = new SentimentCounters();
        for (Ticket ticket : tickets) {
            TicketSentimentResults sentiment = checkNotNull(sentiments.get(ticket.id()));
            authorSentiments.increment(sentiment.authorSentiment());
            if (sentiment.supportSentiment() != null) {
                supportSentiments.increment(sentiment.supportSentiment());
            }
            if (sentiment.othersSentiment() != null) {
                othersSentiments.increment(sentiment.othersSentiment());
            }
        }

        return TicketSentimentCountPerDate.builder()
            .date(date)
            .authorSentiments(authorSentiments.toModel())
            .supportSentiments(supportSentiments.toModel())
            .othersSentiments(othersSentiments.toModel())
            .build();
    }
}
