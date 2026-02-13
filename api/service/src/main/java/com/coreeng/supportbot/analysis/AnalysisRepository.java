package com.coreeng.supportbot.analysis;

import java.util.List;

/**
 * Repository for analysis data.
 */
public interface AnalysisRepository {

    /**
     * Get top 5 categories for "Knowledge Gap" driver with 5 example summaries for each category.
     *
     * @return List of DimensionSummary records with dimension (category), count, and summary
     */
    List<DimensionSummary> getKnowledgeGapCategoriesWithSummaries();

    /**
     * Get top 5 drivers with 5 example summaries for each driver.
     *
     * @return List of DimensionSummary records with dimension (driver), count, and summary
     */
    List<DimensionSummary> getDriversWithSummaries();

    /**
     * Batch upsert analysis records.
     * Inserts new records or updates existing records based on ticket_id.
     *
     * @param records List of analysis records to upsert
     * @return Number of records affected
     */
    int batchUpsert(List<AnalysisRecord> records);

    /**
     * DTO for dimension with summary (used for both categories and drivers)
     */
    record DimensionSummary(String dimension, long queryCount, String summary) {}

    /**
     * DTO for analysis record to be inserted
     */
    record AnalysisRecord(int ticketId, String driver, String category, String feature, String summary) {}
}
