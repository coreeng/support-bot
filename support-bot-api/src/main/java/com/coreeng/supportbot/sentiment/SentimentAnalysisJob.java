package com.coreeng.supportbot.sentiment;

import com.coreeng.supportbot.ticket.TicketId;
import com.google.common.collect.ImmutableList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SentimentAnalysisJob {
    private final SentimentRepository repository;
    private final SentimentService sentimentService;

    @Scheduled(cron = "0 0 0 * * *")
    public void analyzeClosedTickets() {
        ImmutableList<TicketId> ticketIds = repository.listNotAnalysedClosedTickets();
        log.atInfo()
            .addArgument(() -> ticketIds.size()) //NOPMD - suppressed LambdaCanBeMethodReference - It won't compile
            .log("{} tickets to analyse for sentiment");
        for (TicketId ticketId : ticketIds) {
            try {
                log.info("Analysing for sentiment ticket {}", ticketId);
                TicketSentimentResults sentiment = sentimentService.calculateSentiment(ticketId);
                repository.save(ticketId, sentiment);
                log.info("Done analysing for sentiment ticket {}", ticketId);
            } catch (Exception e) {
                log.error("Error calculating sentiment for ticket {}", ticketId, e);
            }
        }
    }
}
