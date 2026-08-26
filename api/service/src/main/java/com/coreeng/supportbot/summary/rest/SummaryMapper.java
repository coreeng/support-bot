package com.coreeng.supportbot.summary.rest;

import com.coreeng.supportbot.summary.SummaryBreakdowns;
import com.coreeng.supportbot.summary.SummaryCount;
import com.coreeng.supportbot.summary.SummaryService.SummaryResult;
import com.coreeng.supportbot.summary.SummaryState;
import com.coreeng.supportbot.summary.rest.SummaryUI.SummaryCountUI;
import com.coreeng.supportbot.summary.rest.SummaryUI.SummaryProgressUI;
import com.coreeng.supportbot.summary.rest.SummaryUI.SummarySectionUI;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** Maps the domain result onto the wire shape. */
@Component
public class SummaryMapper {

    public SummaryUI mapToUI(SummaryResult result) {
        SummaryBreakdowns breakdowns = result.breakdowns();
        return new SummaryUI(
                breakdowns.window().from(),
                breakdowns.window().to(),
                breakdowns.totalTickets(),
                breakdowns.classifiedTickets(),
                breakdowns.unclassifiedTickets(),
                counts(breakdowns.drivers()),
                counts(breakdowns.categories()),
                counts(breakdowns.features()),
                counts(breakdowns.teams()),
                section(result.summary()));
    }

    private static List<SummaryCountUI> counts(ImmutableList<SummaryCount> counts) {
        return counts.stream()
                .map(count -> new SummaryCountUI(count.label(), count.count()))
                .toList();
    }

    private static SummarySectionUI section(SummaryState state) {
        return switch (state) {
            case SummaryState.Ready ready ->
                new SummarySectionUI("ready", ready.content(), ready.model(), ready.generatedAt(), null, null);
            case SummaryState.Generating generating ->
                new SummarySectionUI(
                        "generating",
                        null,
                        null,
                        null,
                        new SummaryProgressUI(
                                generating.phase().name().toLowerCase(Locale.ROOT),
                                generating.analysedThreads(),
                                generating.totalThreads()),
                        null);
            case SummaryState.Unavailable unavailable ->
                new SummarySectionUI("unavailable", null, null, null, null, unavailable.error());
        };
    }
}
