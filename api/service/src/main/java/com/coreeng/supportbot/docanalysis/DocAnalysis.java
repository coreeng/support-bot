package com.coreeng.supportbot.docanalysis;

import com.coreeng.supportbot.ticket.TicketId;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** A ticket's row in {@code doc_analysis}: its result, or its last failed attempt. */
public sealed interface DocAnalysis {

    TicketId ticketId();

    String promptId();

    /**
     * @param docPaths for {@code correct} and {@code incorrect} the one page cited; for {@code missing}
     *     the closest pages, if any
     */
    record Result(
            TicketId ticketId,
            String promptId,
            DocClassification classification,
            String source,
            @Nullable String docsVersion,
            String question,
            @Nullable String answerSummary,
            List<String> tags,
            List<String> productAreas,
            List<String> docPaths,
            @Nullable String evidenceQuote,
            String rationale,
            String model,
            Instant analysedAt)
            implements DocAnalysis {

        public Result {
            tags = List.copyOf(tags);
            productAreas = List.copyOf(productAreas);
            docPaths = List.copyOf(docPaths);
        }
    }

    /**
     * @param reasonCode a fixed code, never model or provider text
     * @param attempts failed attempts under {@code promptId}, this one included
     */
    record FailedAttempt(TicketId ticketId, String promptId, Instant failedAt, String reasonCode, int attempts)
            implements DocAnalysis {}
}
