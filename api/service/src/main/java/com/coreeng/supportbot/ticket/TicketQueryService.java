package com.coreeng.supportbot.ticket;

import com.coreeng.supportbot.escalation.Escalation;
import com.coreeng.supportbot.escalation.EscalationQuery;
import com.coreeng.supportbot.escalation.EscalationQueryService;
import com.coreeng.supportbot.slack.MessageRef;
import com.coreeng.supportbot.slack.SlackTextFormatter;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.coreeng.supportbot.slack.client.SlackGetMessageByTsRequest;
import com.coreeng.supportbot.util.Page;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.slack.api.model.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Nullable;

import static com.google.common.collect.ImmutableList.toImmutableList;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class TicketQueryService {
    private final TicketRepository repository;
    private final EscalationQueryService escalationQueryService;
    private final SlackClient slackClient;
    private final SlackTextFormatter textFormatter;

    public Page<Ticket> findByQuery(TicketsQuery query) {
        return repository.listTickets(query);
    }

    public Page<DetailedTicket> findDetailedTicketByQuery(TicketsQuery query) {
        Page<Ticket> ticketsPage = repository.listTickets(query);

        ImmutableList<TicketId> ticketIds = ticketsPage.content().stream()
                .map(Ticket::id)
                .collect(toImmutableList());

        Page<Escalation> escalationsPage = escalationQueryService.findByQuery(
                EscalationQuery.builder()
                        .ticketIds(ticketIds)
                        .unlimited(true)
                        .build()
        );

        Multimap<TicketId, Escalation> escalationsByTicket = Multimaps.index(
                escalationsPage.content(),
                Escalation::ticketId
        );

        ImmutableList<DetailedTicket> detailedTickets = ticketsPage.content().stream()
                .map(ticket -> new DetailedTicket(
                        ticket,
                        ImmutableList.copyOf(escalationsByTicket.get(ticket.id())),
                        null // Don't fetch query text for list views - only for single ticket fetches
                ))
                .collect(toImmutableList());

        return new Page<>(
                detailedTickets,
                ticketsPage.page(),
                ticketsPage.totalPages(),
                ticketsPage.totalElements()
        );
    }

    @Nullable
    public Ticket findById(TicketId id) {
        return repository.findTicketById(id);
    }

    @Nullable
    public DetailedTicket findDetailedById(TicketId id) {
        Ticket ticket = repository.findTicketById(id);
        if (ticket == null) {
            return null;
        }
        ImmutableList<Escalation> escalations = escalationQueryService.listByTicketId(id);
        String queryText = fetchQueryText(ticket);
        return new DetailedTicket(ticket, escalations, queryText);
    }

    public boolean queryExists(MessageRef queryRef) {
        return repository.queryExists(queryRef);
    }

    @Nullable
    private String fetchQueryText(Ticket ticket) {
        try {
            Message message = slackClient.getMessageByTs(
                new SlackGetMessageByTsRequest(ticket.channelId(), ticket.queryTs())
            );
            String rawText = message.getText();
            return textFormatter.format(rawText);
        } catch (Exception e) {
            if (log.isWarnEnabled()) {
                log.warn("Failed to fetch query message for ticket {}: {}", ticket.id(), e.getMessage());
            }
            return null;
        }
    }
}
