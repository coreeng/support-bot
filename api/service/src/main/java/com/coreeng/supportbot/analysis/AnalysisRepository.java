package com.coreeng.supportbot.analysis;

import com.coreeng.supportbot.ticket.TicketId;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Repository for storing and querying LLM-generated analysis results.
 *
 * <p>This repository manages the {@code analysis} table, which stores:
 * <ul>
 *   <li>Primary driver (e.g., "Knowledge Gap", "Bug", "Feature Request")</li>
 *   <li>Category (e.g., "Monitoring & Troubleshooting")</li>
 *   <li>Platform feature (e.g., "workload compute")</li>
 *   <li>Summary (human-readable explanation)</li>
 *   <li>Prompt ID (for versioning and avoiding re-analysis)</li>
 * </ul>
 */
public interface AnalysisRepository {

    /**
     * Gets the top 5 categories for "Knowledge Gap" driver with up to 5 example summaries
     * for each category.
     *
     * @return List of DimensionSummary records with dimension (category), count, summary, ticketId, and queryTs
     */
    List<DimensionSummary> getKnowledgeGapCategoriesWithSummaries();

    /**
     * Gets the top 5 drivers with up to 5 example summaries for each driver.
     *
     * @return List of DimensionSummary records with dimension (driver), count, summary, ticketId, and queryTs
     */
    List<DimensionSummary> getDriversWithSummaries();

    /**
     * Finds the stored analysis summary for a single ticket.
     *
     * @return the summary text, or {@code null} if no analysis exists or the summary is absent
     */
    @Nullable String findSummaryByTicketId(TicketId ticketId);

    /**
     * Finds stored analysis summaries for the given ticket IDs. Tickets without a stored summary are
     * omitted from the result.
     *
     * @param ticketIds batch of ticket IDs to look up
     * @return summaries keyed by ticket ID (sparse — only tickets with summaries are present)
     */
    ImmutableMap<TicketId, String> findSummariesByTicketIds(ImmutableList<TicketId> ticketIds);

    /**
     * Upserts analysis records.
     * Inserts new records or updates existing records based on {@code ticket_id}.
     *
     * @param records List of analysis records to upsert
     * @return Number of records affected
     */
    int upsert(List<AnalysisRecord> records);

    /**
     * Upserts a single analysis record.
     * Inserts a new record or updates an existing record based on {@code ticket_id}.
     *
     * @param record Analysis record to upsert
     */
    void upsert(AnalysisRecord record);
}
