package com.coreeng.supportbot.ticket.rest;

import com.coreeng.supportbot.config.TicketAssignmentProps;
import com.coreeng.supportbot.ticket.Ticket;
import com.coreeng.supportbot.ticket.TicketId;
import com.coreeng.supportbot.ticket.TicketRepository;
import com.coreeng.supportbot.ticket.TicketStatus;
import com.coreeng.supportbot.ticket.TicketsQuery;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.exception.DataAccessException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Function;

@Slf4j
@Service
@RequiredArgsConstructor
public class BulkReassignmentService {
    private final TicketAssignmentProps assignmentProps;
    private final TicketRepository ticketRepository;

    public BulkReassignResultUI bulkReassign(BulkReassignRequest request) {
        BulkReassignResultUI validationError = validateRequest(request);
        if (validationError != null) {
            return validationError;
        }

        ImmutableMap<TicketId, Ticket> ticketMap = fetchTicketsAsMap(request.ticketIds());

        ImmutableList.Builder<TicketId> successfulIds = ImmutableList.builder();
        ImmutableList.Builder<TicketId> skippedIds = ImmutableList.builder();

        processReassignments(request, ticketMap, successfulIds, skippedIds);

        return buildResult(
                request.ticketIds().size(),
                successfulIds.build(),
                skippedIds.build()
        );
    }

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
        Ticket ticket = ticketMap.get(ticketId);

        if (ticket == null) {
            log.warn("Ticket {} not found, skipping", ticketId);
            skippedIds.add(ticketId);
            return true;
        }

        if (ticket.status() != TicketStatus.opened) {
            log.info("Ticket {} has status {}, skipping bulk reassignment (only OPEN tickets can be bulk-reassigned)",
                    ticketId, ticket.status());
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
            log.warn("Failed to assign ticket {} to {} due to database error: {}", ticketId, assignedTo, e.getMessage());
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
            return String.format("%d of %d tickets successfully reassigned, %d skipped",
                    successCount, totalRequested, skippedCount);
        }
    }

    private BulkReassignResultUI createEmptyResult(String message) {
        return new BulkReassignResultUI(0, ImmutableList.of(), 0, ImmutableList.of(), message);
    }
}

