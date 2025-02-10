package com.coreeng.supportbot.homepage;

import com.coreeng.supportbot.config.SlackTicketsProps;
import com.coreeng.supportbot.enums.ImpactsRegistry;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.coreeng.supportbot.slack.client.SlackGetMessageByTsRequest;
import com.coreeng.supportbot.ticket.Ticket;
import com.coreeng.supportbot.ticket.TicketId;
import com.coreeng.supportbot.ticket.TicketQueryService;
import com.coreeng.supportbot.ticket.TicketStatus;
import com.coreeng.supportbot.util.Page;
import com.google.common.collect.ImmutableList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

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

    public HomepageView getTicketsView(HomepageView.State state) {
        Page<Ticket> page = ticketQueryService.findByQuery(state.toTicketsQuery());
        Map<TicketId, String> permalinkByTicketId = collectPermalinks(page.content());
        return HomepageView.builder()
            .timestamp(Instant.now())
            .tickets(
                page.map(t -> ticketToTicketView(t, permalinkByTicketId.get(t.id())))
                    .content()
            )
            .totalTickets(page.totalElements())
            .totalPages(page.totalPages())
            .page(page.page())
            .channelId(slackTicketsProps.channelId())
            .state(state)
            .build();
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

    private TicketView ticketToTicketView(Ticket t, String permalink) {
        TicketView.TicketViewBuilder view = TicketView.builder()
            .id(t.id())
            .status(t.status())
            .impact(impactsRegistry.findImpactByCode(t.impact()))
            .queryPermalink(permalink);

        if (t.status() == TicketStatus.closed) {
            view.closedAt(
                t.statusLog().stream()
                    .filter(l -> l.status() == TicketStatus.closed)
                    .max(comparing(Ticket.StatusLog::date))
                    .get().date()
            );
        }
        view.lastOpenedAt(
            t.statusLog().stream()
                .filter(l -> l.status() == TicketStatus.opened)
                .max(comparing(Ticket.StatusLog::date))
                .get().date()
        );

        return view.build();
    }
}
