package com.coreeng.supportbot.homepage;

import com.coreeng.supportbot.config.SlackTicketsProps;
import com.coreeng.supportbot.enums.ImpactsRegistry;
import com.coreeng.supportbot.escalation.Escalation;
import com.coreeng.supportbot.escalation.EscalationQuery;
import com.coreeng.supportbot.escalation.EscalationQueryService;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.coreeng.supportbot.slack.client.SlackGetMessageByTsRequest;
import com.coreeng.supportbot.ticket.*;
import com.coreeng.supportbot.util.Page;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Comparator.comparing;

@Service
@RequiredArgsConstructor
@Slf4j
public class HomepageService {
    private final TicketQueryService ticketQueryService;
    private final ExecutorService executor;
    private final SlackClient slackClient;
    private final SlackTicketsProps slackTicketsProps;
    private final ImpactsRegistry impactsRegistry;
    private final EscalationQueryService escalationQueryService;

    public HomepageView getTicketsView(HomepageView.State state) {
        Page<Ticket> page = ticketQueryService.findByQuery(state.toTicketsQuery());

        ImmutableList<TicketId> ticketIds = page.content().stream()
                .map(Ticket::id)
                .collect(toImmutableList());

        Page<Escalation> escalations = escalationQueryService.findByQuery(
                EscalationQuery.builder()
                        .ticketIds(ticketIds)
                        .unlimited(true)
                        .build());

        Multimap<TicketId, Escalation> escalationsByTicketId =
                Multimaps.index(escalations.content(), Escalation::ticketId);

        ImmutableList<DetailedTicket> detailedTickets = getDetailedTickets(state, page, escalationsByTicketId);

        Map<TicketId, String> permalinkByTicketId = collectPermalinks(page.content());

        return HomepageView.builder()
            .timestamp(Instant.now())
            .tickets(
                detailedTickets.stream()
                    .map(dt -> ticketToTicketView(dt, permalinkByTicketId.get(dt.ticket().id())))
                    .collect(toImmutableList())
            )
            .totalTickets(page.totalElements())
            .totalPages(page.totalPages())
            .page(page.page())
            .channelId(slackTicketsProps.channelId())
            .state(state)
            .build();
    }

    private ImmutableList<DetailedTicket> getDetailedTickets(HomepageView.State state, Page<Ticket> page, Multimap<TicketId, Escalation> escalationsByTicketId) {
        Stream<DetailedTicket> detailedTicketStream = page.content().stream()
                .map(ticket -> {
                    ImmutableList<Escalation> escalations =
                            ImmutableList.copyOf(escalationsByTicketId.get(ticket.id()));
                    return new DetailedTicket(ticket, escalations);
                });
        return detailedTicketStream.collect(toImmutableList());
    }

    private Map<TicketId, String> collectPermalinks(ImmutableList<Ticket> tickets) {
        record TicketPermalink(TicketId id, String permalink) {
        }
        CompletionService<TicketPermalink> completionService = new ExecutorCompletionService<>(executor);
        List<Future<TicketPermalink>> futures = new ArrayList<>(tickets.size());
        Map<TicketId, String> result = new HashMap<>();
        try {
            for (Ticket t : tickets) {
                futures.add(completionService.submit(() -> {
                    try {
                        String permalink = slackClient.getPermalink(new SlackGetMessageByTsRequest(
                            t.channelId(),
                            t.queryTs()
                        ));
                        return new TicketPermalink(t.id(), permalink);
                    } catch (Exception e) {
                        log.atError()
                            .setCause(e)
                            .addArgument(t::id)
                            .log("Error while collecting permalink for ticket {}");
                        throw e;
                    }
                }));
            }
            for (int i = 0; i < futures.size(); i++) { //NOPMD - suppressed ForLoopCanBeForeach - the loop is not foreach in principle
                TicketPermalink tp = completionService.take().get();
                result.put(tp.id(), tp.permalink());
            }
        } catch (Exception e) {
            for (Future<TicketPermalink> f : futures) {
                f.cancel(false);
            }
            log.atError()
                .setCause(e)
                .log("Error while collecting permalinks");
            throw new RuntimeException(e);
        }
        return result;
    }

    private TicketView ticketToTicketView(DetailedTicket t, String permalink) {
        Ticket ticket = t.ticket();
        TicketView.TicketViewBuilder view = TicketView.builder()
            .id(ticket.id())
            .status(ticket.status())
            .escalations(t.escalations())
            .impact(impactsRegistry.findImpactByCode(ticket.impact()))
            .queryPermalink(permalink);

        if (ticket.status() == TicketStatus.closed) {
            view.closedAt(
                ticket.statusLog().stream()
                    .filter(l -> l.status() == TicketStatus.closed)
                    .max(comparing(Ticket.StatusLog::date))
                    .get().date()
            );
        }
        view.lastOpenedAt(
            ticket.statusLog().stream()
                .filter(l -> l.status() == TicketStatus.opened)
                .max(comparing(Ticket.StatusLog::date))
                .get().date()
        );

        return view.build();
    }
}
