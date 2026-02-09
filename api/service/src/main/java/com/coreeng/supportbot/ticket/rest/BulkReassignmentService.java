package com.coreeng.supportbot.ticket.rest;

import com.coreeng.supportbot.config.TicketAssignmentProps;
import com.coreeng.supportbot.slack.SlackException;
import com.coreeng.supportbot.slack.SlackId;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.coreeng.supportbot.slack.client.SlackMessage;
import com.coreeng.supportbot.slack.client.SimpleSlackMessage;
import com.coreeng.supportbot.slack.client.SlackGetMessageByTsRequest;
import com.coreeng.supportbot.slack.client.SlackPostMessageRequest;
import com.coreeng.supportbot.ticket.Ticket;
import com.coreeng.supportbot.ticket.TicketId;
import com.coreeng.supportbot.ticket.TicketRepository;
import com.coreeng.supportbot.ticket.TicketsQuery;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.exception.DataAccessException;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Function;

import static java.lang.String.format;

@Slf4j
@Service
@RequiredArgsConstructor
public class BulkReassignmentService {
    private final TicketAssignmentProps assignmentProps;
    private final TicketRepository ticketRepository;
    private final SlackClient slackClient;

    public BulkReassignResultUI bulkReassign(BulkReassignRequest request) {
        BulkReassignResultUI validationError = validateRequest(request);
        if (validationError != null) {
            return validationError;
        }

        ImmutableMap<TicketId, Ticket> ticketMap = fetchTicketsAsMap(request.ticketIds());

        ImmutableList.Builder<TicketId> successfulIds = ImmutableList.builder();
        ImmutableList.Builder<TicketId> skippedIds = ImmutableList.builder();

        processReassignments(request, ticketMap, successfulIds, skippedIds);

        ImmutableList<TicketId> successfulTicketIds = successfulIds.build();
        
        // Send notification to assignee if any tickets were successfully assigned
        if (!successfulTicketIds.isEmpty()) {
            sendAssignmentNotification(request.assignedTo(), successfulTicketIds, ticketMap);
        }

        return buildResult(
            request.ticketIds().size(),
            successfulTicketIds,
            skippedIds.build()
        );
    }

    @Nullable
    private BulkReassignResultUI validateRequest(BulkReassignRequest request) {
        if (!assignmentProps.enabled()) {
            return createEmptyResult("Assignment feature is disabled");
        }

        if (request.ticketIds() == null || request.ticketIds().isEmpty()) {
            return createEmptyResult("Ticket IDs list cannot be empty");
        }

        if (request.assignedTo() == null || request.assignedTo().isBlank()) {
            return createEmptyResult("Assignee Slack ID is required");
        }

        return null;
    }

    private ImmutableMap<TicketId, Ticket> fetchTicketsAsMap(List<TicketId> ticketIds) {
        TicketsQuery query = TicketsQuery.builder()
                .ids(ImmutableList.copyOf(ticketIds))
                .excludeClosed(true)
                .unlimited(true)
                .build();

        ImmutableList<Ticket> tickets = ticketRepository.listTickets(query).content();

        return tickets.stream()
                .collect(ImmutableMap.toImmutableMap(Ticket::id, Function.identity()));
    }

    private void processReassignments(
            BulkReassignRequest request,
            ImmutableMap<TicketId, Ticket> ticketMap,
            ImmutableList.Builder<TicketId> successfulIds,
            ImmutableList.Builder<TicketId> skippedIds
    ) {
        for (TicketId ticketId : request.ticketIds()) {
            if (shouldSkipTicket(ticketId, ticketMap, skippedIds)) {
                continue;
            }

            if (tryAssignTicket(ticketId, request.assignedTo())) {
                successfulIds.add(ticketId);
            } else {
                skippedIds.add(ticketId);
            }
        }
    }

    private boolean shouldSkipTicket(
            TicketId ticketId,
            ImmutableMap<TicketId, Ticket> ticketMap,
            ImmutableList.Builder<TicketId> skippedIds
    ) {
        if (!ticketMap.containsKey(ticketId)) {
            log.warn("Ticket {} not found or is CLOSED, skipping", ticketId);
            skippedIds.add(ticketId);
            return true;
        }

        return false;
    }

    private boolean tryAssignTicket(TicketId ticketId, String assignedTo) {
        try {
            ticketRepository.assign(ticketId, assignedTo);
            return true;
        } catch (DataAccessException e) {
            if (log.isWarnEnabled()) {
                log.warn("Failed to assign ticket {} to {} due to database error: {}", ticketId, assignedTo, e.getMessage());
            }
            return false;
        }
    }

    private BulkReassignResultUI buildResult(
            int totalRequested,
            ImmutableList<TicketId> successfulIds,
            ImmutableList<TicketId> skippedIds
    ) {
        int successCount = successfulIds.size();
        int skippedCount = skippedIds.size();
        String message = buildResultMessage(totalRequested, successCount, skippedCount);

        return new BulkReassignResultUI(
                successCount,
                successfulIds,
                skippedCount,
                skippedIds,
                message
        );
    }

    private String buildResultMessage(int totalRequested, int successCount, int skippedCount) {
        if (successCount == totalRequested) {
            return "All tickets successfully reassigned";
        } else if (successCount == 0) {
            return "No tickets were reassigned (all were skipped or failed)";
        } else {
            return format("%d of %d tickets successfully reassigned, %d skipped",
                    successCount, totalRequested, skippedCount);
        }
    }

    private BulkReassignResultUI createEmptyResult(String message) {
        return new BulkReassignResultUI(0, ImmutableList.of(), 0, ImmutableList.of(), message);
    }

    private void sendAssignmentNotification(
        String assignedToUserId,
        ImmutableList<TicketId> successfulTicketIds,
        ImmutableMap<TicketId, Ticket> ticketMap
    ) {
        try {
            // Open DM conversation with the assignee
            var dmResponse = slackClient.openDmConversation(SlackId.user(assignedToUserId));
            if (!dmResponse.isOk() || dmResponse.getChannel() == null) {
                if (log.isWarnEnabled()) {
                    log.warn("Failed to open DM conversation with user {}: {}", assignedToUserId, dmResponse.getError());
                }
                return;
            }

            String dmChannelId = dmResponse.getChannel().getId();
            
            SlackMessage message = buildAssignmentNotificationMessage(successfulTicketIds, ticketMap);
            
            // Send the message to the DM channel
            slackClient.postMessage(new SlackPostMessageRequest(message, dmChannelId, null));
            if (log.isInfoEnabled()) {
                log.info("Sent assignment notification to user {} for {} tickets", assignedToUserId, successfulTicketIds.size());
            }
        } catch (SlackException e) {
            if (log.isWarnEnabled()) {
                log.warn("Failed to send assignment notification to user {}: {}", assignedToUserId, e.getMessage(), e);
            }
        }
    }

    private SlackMessage buildAssignmentNotificationMessage(
        ImmutableList<TicketId> ticketIds,
        ImmutableMap<TicketId, Ticket> ticketMap
    ) {
        String header = format("*You have been assigned to %d ticket%s:*\n\n", 
            ticketIds.size(), 
            ticketIds.size() == 1 ? "" : "s");

        StringBuilder ticketList = new StringBuilder(header);

        ticketIds.forEach(ticketId -> {
            Ticket ticket = ticketMap.get(ticketId);
            if (ticket != null) {
                String permalink = slackClient.getPermalink(new SlackGetMessageByTsRequest(
                    ticket.channelId(),
                    ticket.queryTs()
                ));
                ticketList.append(format("• <%s|Ticket %s>\n", permalink, ticketId.render()));
            } else {
                ticketList.append(format("• Ticket %s\n", ticketId.render()));
            }
        });

        return SimpleSlackMessage.builder()
            .text(ticketList.toString())
            .build();
    }
}
