package com.coreeng.supportbot.docanalysis;

import com.coreeng.supportbot.analysis.AnalysisPromptLoadException;
import com.coreeng.supportbot.config.ConditionalOnLlmEnabled;
import com.coreeng.supportbot.config.SlackChannelRegistry;
import com.coreeng.supportbot.docanalysis.DocAnalysisReport.RunStatus;
import com.coreeng.supportbot.docanalysis.DocAnalysisRunService.StartResult;
import com.coreeng.supportbot.docanalysis.DocAnalysisRunState.Progress;
import com.coreeng.supportbot.docanalysis.DocAnalysisRunState.StopReason;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnLlmEnabled
@ConditionalOnProperty(name = "documentation-analysis.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class DocAnalysisReportService {

    private final DocAnalysisRunService runService;
    private final DocClassifier classifier;
    private final DocAnalysisRepository repository;
    private final DocumentationSource source;
    private final SlackChannelRegistry channelRegistry;
    private final Clock clock;

    /**
     * Starts a run over the window when one is due, without waiting for it, and reports the window.
     *
     * @param from first day of the window, inclusive, in UTC
     * @param to last day of the window, inclusive, in UTC
     */
    public DocAnalysisReport report(LocalDate from, LocalDate to) {
        StartResult started;
        String promptId;
        try {
            started = runService.start(from, to);
            promptId = classifier.currentPromptId();
        } catch (AnalysisPromptLoadException e) {
            log.warn("Documentation analysis for {}..{} not run: {}", from, to, e.getMessage());
            return new DocAnalysisReport(
                    from,
                    to,
                    tickets(from, to, null),
                    new RunStatus.NoPrompt(),
                    source.status().orElse(null));
        }
        RunStatus run = runStatus(from, to, started, runService.state());
        return new DocAnalysisReport(
                from, to, tickets(from, to, promptId), run, source.status().orElse(null));
    }

    private List<WindowTicket> tickets(LocalDate from, LocalDate to, @Nullable String promptId) {
        return repository.findWindow(from, to, channelRegistry.monitoredChannelIds()).stream()
                .map(ticket -> isCurrentOrNone(ticket.analysis(), promptId)
                        ? ticket
                        : new WindowTicket(
                                ticket.ticketId(), ticket.channelId(), ticket.threadTs(), ticket.raisedAt(), null))
                .toList();
    }

    private static boolean isCurrentOrNone(@Nullable DocAnalysis analysis, @Nullable String promptId) {
        return analysis == null || analysis.promptId().equals(promptId);
    }

    /**
     * The run state is one for the whole instance, so a reason a run stopped is shown only when it
     * can still be what holds this window up.
     */
    private RunStatus runStatus(LocalDate from, LocalDate to, StartResult started, DocAnalysisRunState state) {
        Progress active = state.active();
        if (active != null) {
            return active.from().equals(from) && active.to().equals(to)
                    ? new RunStatus.Running(active)
                    : new RunStatus.Busy(active);
        }
        if (started == StartResult.ALREADY_ACTIVE) {
            return new RunStatus.Busy(null);
        }
        StopReason stopped = state.stopped();
        if (started == StartResult.NOTHING_DUE || stopped == null) {
            return new RunStatus.Idle();
        }
        if (stopped instanceof StopReason.ProviderOutage outage
                && !clock.instant().isBefore(outage.retryAt())) {
            return new RunStatus.Idle();
        }
        return new RunStatus.Stopped(stopped);
    }
}
