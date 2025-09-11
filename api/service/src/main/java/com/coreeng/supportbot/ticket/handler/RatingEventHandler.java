package com.coreeng.supportbot.ticket.handler;

import com.coreeng.supportbot.slack.MessageRef;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.coreeng.supportbot.slack.client.SlackGetMessageByTsRequest;
import com.coreeng.supportbot.ticket.Ticket;
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
        if (event.status() == TicketStatus.closed) {
            // Trigger rating collection when ticket is closed
            Ticket ticket = ticketRepository.findTicketById(event.ticketId());
            if (ticket != null) {
                // Check if ticket is already rated using the ticket object
                if (ticket.ratingSubmitted()) {
                    if (log.isInfoEnabled()) {
                        log.info("Ticket {} already has a rating submitted, skipping rating request", event.ticketId());
                    }
                    return;
                }
                
                if (log.isInfoEnabled()) {
                    log.info("Ticket {} closed, posting rating request", event.ticketId());
                }
                
                try {
                    // Get the original query message to find the user who created the ticket
                    var originalMessage = slackClient.getMessageByTs(SlackGetMessageByTsRequest.of(ticket.queryRef()));
                    var threadRef = new MessageRef(ticket.createdMessageTs(), ticket.createdMessageTs(), ticket.channelId());
                    slackService.postRatingRequest(threadRef, event.ticketId(), originalMessage.getUser());
                } catch (Exception e) {
                    if (log.isErrorEnabled()) {
                        log.error("Error getting original message for ticket {} rating request", event.ticketId(), e);
                    }
                }
            } else {
                if (log.isWarnEnabled()) {
                    log.warn("Could not find ticket {} to post rating request", event.ticketId());
                }
            }
        }
    }
}

