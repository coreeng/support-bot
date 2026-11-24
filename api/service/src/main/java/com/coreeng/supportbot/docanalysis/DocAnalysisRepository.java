package com.coreeng.supportbot.docanalysis;

import com.coreeng.supportbot.ticket.TicketId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Each save replaces whatever the ticket's row held before. */
public interface DocAnalysisRepository {

    void saveResult(DocAnalysis.Result result);

    void saveFailedAttempt(DocAnalysis.FailedAttempt attempt);

    Optional<DocAnalysis> findByTicketId(TicketId ticketId);

    /**
     * Closed tickets raised in the window, in the given channels, with no row under {@code promptId}
     * or a failed attempt under it from no later than {@code failedBefore} with fewer than {@code
     * maxAttempts} attempts. Oldest first.
     *
     * @param from first day of the window, inclusive, in UTC
     * @param to last day of the window, inclusive, in UTC
     */
    List<TicketToClassify> findDue(
            LocalDate from,
            LocalDate to,
            Collection<String> channelIds,
            String promptId,
            Instant failedBefore,
            int maxAttempts);

    /**
     * Closed tickets raised in the window, in the given channels, each with its row whatever its
     * {@code prompt_id}, or none.
     *
     * @param from first day of the window, inclusive, in UTC
     * @param to last day of the window, inclusive, in UTC
     */
    List<WindowTicket> findWindow(LocalDate from, LocalDate to, Collection<String> channelIds);

    /** Tickets whose row, a result or a failed attempt, was saved at or after {@code since}. */
    int countAttemptedSince(Instant since);

    /** Removes the tickets' failed attempts with this reason code; any other row is kept. */
    void deleteFailedAttempts(Collection<TicketId> ticketIds, String reasonCode);
}
