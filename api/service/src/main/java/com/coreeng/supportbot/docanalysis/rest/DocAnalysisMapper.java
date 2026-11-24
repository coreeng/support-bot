package com.coreeng.supportbot.docanalysis.rest;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

import com.coreeng.supportbot.docanalysis.DocAnalysis;
import com.coreeng.supportbot.docanalysis.DocAnalysisReport;
import com.coreeng.supportbot.docanalysis.DocAnalysisReport.RunStatus;
import com.coreeng.supportbot.docanalysis.DocAnalysisRunState.Progress;
import com.coreeng.supportbot.docanalysis.DocAnalysisRunState.StopReason;
import com.coreeng.supportbot.docanalysis.DocClassification;
import com.coreeng.supportbot.docanalysis.SourceStatus;
import com.coreeng.supportbot.docanalysis.WindowTicket;
import com.coreeng.supportbot.docanalysis.rest.DocAnalysisUI.AreaCountsUI;
import com.coreeng.supportbot.docanalysis.rest.DocAnalysisUI.AreaGroupUI;
import com.coreeng.supportbot.docanalysis.rest.DocAnalysisUI.CountsUI;
import com.coreeng.supportbot.docanalysis.rest.DocAnalysisUI.DocumentationUI;
import com.coreeng.supportbot.docanalysis.rest.DocAnalysisUI.PageGroupUI;
import com.coreeng.supportbot.docanalysis.rest.DocAnalysisUI.ProgressUI;
import com.coreeng.supportbot.docanalysis.rest.DocAnalysisUI.ResultUI;
import com.coreeng.supportbot.docanalysis.rest.DocAnalysisUI.RunUI;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/** Maps a window's report onto the wire shape, counting and grouping its results. */
@Component
public class DocAnalysisMapper {

    private static final List<DocClassification> LISTING_ORDER = List.of(
            DocClassification.INCORRECT,
            DocClassification.MISSING,
            DocClassification.CORRECT,
            DocClassification.NOT_APPLICABLE);

    private static final Comparator<Classified> LISTING = Comparator.<Classified>comparingInt(
                    classified -> LISTING_ORDER.indexOf(classified.result().classification()))
            .thenComparing(classified -> classified.ticket().raisedAt(), Comparator.reverseOrder())
            .thenComparing(classified -> classified.ticket().ticketId().id(), Comparator.reverseOrder());

    public DocAnalysisUI mapToUI(DocAnalysisReport report) {
        List<Classified> classified = report.tickets().stream()
                .flatMap(ticket -> ticket.analysis() instanceof DocAnalysis.Result result
                        ? Stream.of(new Classified(ticket, result))
                        : Stream.empty())
                .sorted(LISTING)
                .toList();
        return new DocAnalysisUI(
                report.from(),
                report.to(),
                counts(report.tickets(), classified),
                areaCounts(classified),
                citedPages(classified),
                missingByArea(classified),
                classified.stream().map(DocAnalysisMapper::result).toList(),
                run(report.run()),
                documentation(report.documentation()));
    }

    private static CountsUI counts(List<WindowTicket> tickets, List<Classified> classified) {
        return new CountsUI(
                tickets.size(),
                count(classified, DocClassification.CORRECT),
                count(classified, DocClassification.MISSING),
                count(classified, DocClassification.INCORRECT),
                count(classified, DocClassification.NOT_APPLICABLE),
                (long) tickets.size() - classified.size(),
                tickets.stream()
                        .filter(ticket -> ticket.analysis() instanceof DocAnalysis.FailedAttempt)
                        .count());
    }

    private static List<AreaCountsUI> areaCounts(List<Classified> classified) {
        return byArea(classified.stream()).stream()
                .map(group -> new AreaCountsUI(
                        group.getKey().orElse(null),
                        group.getValue().size(),
                        count(group.getValue(), DocClassification.CORRECT),
                        count(group.getValue(), DocClassification.MISSING),
                        count(group.getValue(), DocClassification.INCORRECT),
                        count(group.getValue(), DocClassification.NOT_APPLICABLE)))
                .toList();
    }

    private static List<PageGroupUI> citedPages(List<Classified> classified) {
        Map<String, List<Classified>> byPage = classified.stream()
                .filter(each -> each.is(DocClassification.CORRECT) || each.is(DocClassification.INCORRECT))
                .filter(each -> !each.result().docPaths().isEmpty())
                .collect(groupingBy(each -> each.result().docPaths().getFirst(), LinkedHashMap::new, toList()));
        return byPage.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, List<Classified>>>comparingInt(
                                group -> -group.getValue().size())
                        .thenComparing(Map.Entry::getKey))
                .map(group -> new PageGroupUI(
                        group.getKey(),
                        group.getValue().size(),
                        count(group.getValue(), DocClassification.CORRECT),
                        count(group.getValue(), DocClassification.INCORRECT),
                        ticketIds(group.getValue())))
                .toList();
    }

    private static List<AreaGroupUI> missingByArea(List<Classified> classified) {
        return byArea(classified.stream().filter(each -> each.is(DocClassification.MISSING))).stream()
                .map(group -> new AreaGroupUI(
                        group.getKey().orElse(null), group.getValue().size(), ticketIds(group.getValue())))
                .toList();
    }

    /** Largest group first, by name among equals, and the tickets with no area last. */
    private static List<Map.Entry<Optional<String>, List<Classified>>> byArea(Stream<Classified> classified) {
        Function<Classified, Optional<String>> area = Classified::area;
        Map<Optional<String>, List<Classified>> groups =
                classified.collect(groupingBy(area, LinkedHashMap::new, toList()));
        return groups.entrySet().stream()
                .sorted(Comparator.<Map.Entry<Optional<String>, List<Classified>>, Boolean>comparing(
                                group -> group.getKey().isEmpty())
                        .thenComparingInt(group -> -group.getValue().size())
                        .thenComparing(group -> group.getKey().orElse("")))
                .toList();
    }

    private static long count(List<Classified> classified, DocClassification classification) {
        return classified.stream().filter(each -> each.is(classification)).count();
    }

    private static List<String> ticketIds(List<Classified> classified) {
        return classified.stream().map(Classified::ticketId).toList();
    }

    private static ResultUI result(Classified classified) {
        WindowTicket ticket = classified.ticket();
        DocAnalysis.Result result = classified.result();
        return new ResultUI(
                classified.ticketId(),
                ticket.channelId(),
                ticket.threadTs(),
                ticket.raisedAt(),
                result.classification().code(),
                result.question(),
                result.answerSummary(),
                result.tags(),
                result.docPaths(),
                result.evidenceQuote(),
                result.rationale(),
                classified.area().orElse(null),
                result.productAreas(),
                result.docsVersion(),
                result.analysedAt());
    }

    private static RunUI run(RunStatus status) {
        return switch (status) {
            case RunStatus.Running running -> new RunUI("running", progress(running.progress()), null, null);
            case RunStatus.Busy busy -> new RunUI("busy", progress(busy.progress()), null, null);
            case RunStatus.Idle _ -> new RunUI("idle", null, null, null);
            case RunStatus.NoPrompt _ -> new RunUI("no_prompt", null, null, null);
            case RunStatus.Stopped stopped ->
                switch (stopped.reason()) {
                    case StopReason.SourceUnavailable unavailable ->
                        new RunUI("source_unavailable", null, unavailable.reason(), null);
                    case StopReason.ProviderOutage outage -> new RunUI("provider_outage", null, null, outage.retryAt());
                    case StopReason.DailyLimitReached _ -> new RunUI("daily_limit_reached", null, null, null);
                    case StopReason.Failed _ -> new RunUI("failed", null, null, null);
                };
        };
    }

    private static @Nullable ProgressUI progress(@Nullable Progress progress) {
        return progress == null
                ? null
                : new ProgressUI(progress.from(), progress.to(), progress.done(), progress.total());
    }

    private static @Nullable DocumentationUI documentation(@Nullable SourceStatus status) {
        return status == null ? null : new DocumentationUI(status.version(), status.refreshedAt());
    }

    private record Classified(WindowTicket ticket, DocAnalysis.Result result) {

        boolean is(DocClassification classification) {
            return result.classification() == classification;
        }

        Optional<String> area() {
            return result.productAreas().stream().findFirst();
        }

        String ticketId() {
            return String.valueOf(ticket.ticketId().id());
        }
    }
}
