package com.coreeng.supportbot.sentiment.rest;

import com.coreeng.supportbot.sentiment.SentimentAnalysisJob;
import com.coreeng.supportbot.sentiment.SentimentQueryService;
import com.coreeng.supportbot.sentiment.TicketSentimentResults;
import com.coreeng.supportbot.ticket.TicketId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@RequestMapping("/sentiment")
@RequiredArgsConstructor
public class SentimentAnalysisController {
    private final SentimentAnalysisJob job;
    private final SentimentQueryService queryService;
    private final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();

    @PostMapping("/ticket/job")
    public void calculateSentiment() {
        executorService.submit(job::analyzeClosedTickets);
    }

    @GetMapping("/ticket/{id}")
    public TicketSentimentResults getSentiment(@PathVariable("id") TicketId ticketId) {
        return queryService.findByTicketId(ticketId);
    }
}
