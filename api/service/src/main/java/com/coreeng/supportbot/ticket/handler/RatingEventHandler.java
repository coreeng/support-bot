package com.coreeng.supportbot.ticket.handler;

import com.coreeng.supportbot.slack.client.SlackClient;
import com.coreeng.supportbot.slack.client.SlackGetMessageByTsRequest;
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
            var ticket = ticketRepository.findTicketById(event.ticketId());
            if (ticket != null) {
                if (log.isInfoEnabled()) {
                    log.info("Ticket {} closed, posting rating request", event.ticketId());
                }
                
                try {
                    // Get the original query message to find the user who created the ticket
                    var originalMessage = slackClient.getMessageByTs(SlackGetMessageByTsRequest.of(ticket.queryRef()));
                    
                    String userId = originalMessage.getUser();
                    if (userId != null) {
                        slackService.postRatingRequest(ticket.queryRef(), event.ticketId(), userId);
                    } else {
                        if (log.isWarnEnabled()) {
                            log.warn("Could not determine user for ticket {} rating request", event.ticketId());
                        }
                    }
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

