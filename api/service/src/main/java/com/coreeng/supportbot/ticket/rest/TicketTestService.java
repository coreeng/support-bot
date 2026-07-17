package com.coreeng.supportbot.ticket.rest;

import static java.util.Objects.requireNonNull;

import com.coreeng.supportbot.slack.MessageRef;
import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.ticket.DetailedTicket;
import com.coreeng.supportbot.ticket.Ticket;
import com.coreeng.supportbot.ticket.TicketId;
import com.coreeng.supportbot.ticket.TicketProcessingService;
import com.coreeng.supportbot.ticket.TicketQueryService;
import com.coreeng.supportbot.ticket.TicketRepository;
import com.coreeng.supportbot.ticket.TicketSubmission;
import com.coreeng.supportbot.ticket.TicketSubmitResult;
import com.coreeng.supportbot.ticket.TicketTeam;
import com.google.common.collect.ImmutableList;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Persistence-backed fixture operations used only by the functional-test HTTP surface. */
@Service
@Profile({"functionaltests", "nft"})
@RequiredArgsConstructor
public class TicketTestService {
    private final TicketRepository repository;
    private final TicketQueryService queryService;
    private final TicketUIMapper mapper;
    private final TicketProcessingService ticketProcessingService;

    @Transactional
    public TicketUI createTicket(TicketTestController.TicketToCreate request) {
        Ticket ticket = repository.createTicketIfNotExists(
                Ticket.createNew(MessageTs.of(request.queryTs()), request.channelId()).toBuilder()
                        .createdMessageTs(MessageTs.ofOrNull(request.createdMessageTs()))
                        .build());
        TicketId ticketId = requireNonNull(ticket.id());
        DetailedTicket detailedTicket = queryService.findDetailedById(ticketId);
        String queryText = detailedTicket != null ? queryService.fetchQueryText(detailedTicket.ticket()) : null;
        return mapper.mapToUI(requireNonNull(detailedTicket), queryText);
    }

    @Nullable public TicketUI findTicketByQuery(String channelId, String messageTs) {
        DetailedTicket detailedTicket =
                queryService.findDetailedByQueryRef(new MessageRef(MessageTs.of(messageTs), channelId));
        return detailedTicket == null ? null : mapper.mapToUI(detailedTicket);
    }

    @Transactional
    public UpdateResult updateTicket(TicketTestController.TicketToUpdate request) {
        TicketId ticketId = new TicketId(request.ticketId());
        TicketSubmission submission = TicketSubmission.builder()
                .ticketId(ticketId)
                .status(request.status())
                .authorsTeam(TicketTeam.fromCode(request.authorsTeam()))
                .tags(ImmutableList.copyOf(request.tags()))
                .impact(request.impact())
                .confirmed(true)
                .build();

        TicketSubmitResult result = ticketProcessingService.submit(submission);
        if (!(result instanceof TicketSubmitResult.Success)) {
            return new UpdateResult(null, "Expected a successful result, got: " + result);
        }
        DetailedTicket ticket = queryService.findDetailedById(ticketId);
        return new UpdateResult(mapper.mapToUI(requireNonNull(ticket)), null);
    }

    public record UpdateResult(
            @Nullable TicketUI ticket, @Nullable String error) {}
}
