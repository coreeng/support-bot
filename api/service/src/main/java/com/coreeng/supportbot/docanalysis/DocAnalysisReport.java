package com.coreeng.supportbot.docanalysis;

import com.coreeng.supportbot.docanalysis.DocAnalysisRunState.Progress;
import com.coreeng.supportbot.docanalysis.DocAnalysisRunState.StopReason;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A window's closed tickets and what the run is doing for it.
 *
 * @param tickets each with its row only when the row is current; null otherwise
 * @param documentation what the source holds now; null when it holds nothing yet
 */
public record DocAnalysisReport(
        LocalDate from,
        LocalDate to,
        List<WindowTicket> tickets,
        RunStatus run,
        @Nullable SourceStatus documentation) {

    public DocAnalysisReport {
        tickets = List.copyOf(tickets);
    }

    public sealed interface RunStatus {

        record Running(Progress progress) implements RunStatus {}

        /**
         * A run is active for another window, or on another instance, for whichever window; its
         * progress is null in the second case.
         */
        record Busy(@Nullable Progress progress) implements RunStatus {}

        /** Nothing is running and nothing stops a run: the window has nothing due, or it just finished. */
        record Idle() implements RunStatus {}

        record Stopped(StopReason reason) implements RunStatus {}

        record NoPrompt() implements RunStatus {}
    }
}
