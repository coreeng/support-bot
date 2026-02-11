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
     * DTO for dimension with summary (used for both categories and drivers)
     */
    record DimensionSummary(String dimension, long queryCount, String summary) {}
}
