package com.coreeng.supportbot.ticket.handler;

import com.coreeng.supportbot.slack.client.SlackClient;
import com.coreeng.supportbot.slack.client.SlackGetMessageByTsRequest;
import com.coreeng.supportbot.ticket.Ticket;
import com.coreeng.supportbot.ticket.TicketId;
import com.coreeng.supportbot.ticket.TicketRepository;
import com.coreeng.supportbot.ticket.TicketStatus;
import com.coreeng.supportbot.ticket.TicketStatusChanged;
import com.coreeng.supportbot.ticket.slack.TicketSlackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RatingEventHandler {
    private final TicketSlackService slackService;
    private final TicketRepository ticketRepository;
    private final SlackClient slackClient;

    @EventListener
    public void onTicketStatusChange(TicketStatusChanged event) {
        if (event.status() != TicketStatus.closed) {
            return;
        }
        TicketId ticketId = event.ticketId();
        Ticket ticket = ticketRepository.findTicketById(ticketId);
        if (ticket == null) {
            log.warn("Ticket {} not found when publishing rating request", ticketId);
            return;
        }
        if (ticket.ratingSubmitted()) {
            log.info("Ticket {} already has a rating submitted, skipping rating request", ticketId);
            return;
        }

        log.info("Ticket {} closed, posting rating request", ticketId);
        try {
            // Get the original query message to find the user who created the ticket
            var originalMessage = slackClient.getMessageByTs(SlackGetMessageByTsRequest.of(ticket.queryRef()));
            slackService.postRatingRequest(ticket.queryRef(), ticketId, originalMessage.getUser());
        } catch (Exception e) {
            log.error("Error posting rating request for ticket {}", ticketId, e);
        }
    }
}

