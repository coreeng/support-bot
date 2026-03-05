package com.coreeng.supportbot.analysis;

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
     * @return List of DimensionSummary records with dimension (category), count, summary, channelId, and queryTs
     */
    List<DimensionSummary> getKnowledgeGapCategoriesWithSummaries();

    /**
     * Gets the top 5 drivers with up to 5 example summaries for each driver.
     *
     * @return List of DimensionSummary records with dimension (driver), count, summary, channelId, and queryTs
     */
    List<DimensionSummary> getDriversWithSummaries();

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

    /**
     * DTO for a dimension (driver or category) with its count and example summaries.
     *
     * @param dimension The dimension value (e.g., "Knowledge Gap" or "Monitoring & Troubleshooting")
     * @param queryCount Total number of tickets with this dimension
     * @param summary One example summary for this dimension
     */
    record DimensionSummary(String dimension, long queryCount, String summary, String channelId, String queryTs) {}

    /**
     * DTO for an analysis record.
     *
     * @param ticketId The ticket ID (primary key)
     * @param driver The primary driver (e.g., "Knowledge Gap")
     * @param category The category (e.g., "Monitoring & Troubleshooting")
     * @param feature The platform feature (e.g., "workload compute")
     * @param summary Human-readable explanation of the ticket
     * @param promptId The prompt ID used to generate this analysis (for versioning)
     */
    record AnalysisRecord(
            int ticketId,
            @Nullable String driver,
            @Nullable String category,
            @Nullable String feature,
            @Nullable String summary,
            @Nullable String promptId) {

        /**
         * Checks if this record has all required fields populated.
         *
         * @return true if ticketId is positive and all analysis fields are non-blank
         */
        public boolean isValid() {
            return ticketId > 0 && isValid(driver) && isValid(category) && isValid(feature) && isValid(summary);
        }

        private boolean isValid(@Nullable String s) {
            return s != null && !s.isBlank();
        }
    }
}
