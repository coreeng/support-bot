package com.coreeng.supportbot.analysis;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for analysis data operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AnalysisService {

    private final AnalysisRepository analysisRepository;

    /**
     * Get top 5 knowledge gap categories with 5 example summaries for each category.
     *
     * @return List of DimensionSummary records with dimension (category), count, and summary
     */
    public List<AnalysisRepository.DimensionSummary> getKnowledgeGapCategoriesWithSummaries() {
        return analysisRepository.getKnowledgeGapCategoriesWithSummaries();
    }

    /**
     * Get top 5 drivers with 5 example summaries for each driver.
     *
     * @return List of DimensionSummary records with dimension (driver), count, and summary
     */
    public List<AnalysisRepository.DimensionSummary> getDriversWithSummaries() {
        return analysisRepository.getDriversWithSummaries();
    }

    /**
     * Import analysis data from a list of records.
     * Inserts new records or updates existing records based on ticket_id.
     *
     * @param records List of analysis records to import
     * @return Number of records affected
     */
    @Transactional
    public int importAnalysisData(List<AnalysisRepository.AnalysisRecord> records) {
        log.info("Starting analysis data import with {} records", records.size());

        // Upsert records (insert new or update existing based on ticket_id)
        int affectedCount = analysisRepository.batchUpsert(records);
        log.info("Upserted {} analysis records", affectedCount);

        return affectedCount;
    }
}
