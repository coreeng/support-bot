package com.coreeng.supportbot.docanalysis;

import com.coreeng.supportbot.ticket.TicketId;
import java.util.Optional;

/** Each save replaces whatever the ticket's row held before. */
public interface DocAnalysisRepository {

    void saveResult(DocAnalysis.Result result);

    void saveFailedAttempt(DocAnalysis.FailedAttempt attempt);

    Optional<DocAnalysis> findByTicketId(TicketId ticketId);
}
