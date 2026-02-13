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
     * Deletes all existing data and inserts new records in a single transaction.
     *
     * @param records List of analysis records to import
     * @return Number of records imported
     */
    @Transactional
    public int importAnalysisData(List<AnalysisRepository.AnalysisRecord> records) {
        log.info("Starting analysis data import with {} records", records.size());

        // Delete all existing records
        int deletedCount = analysisRepository.deleteAll();
        log.info("Deleted {} existing analysis records", deletedCount);

        // Insert new records
        int insertedCount = analysisRepository.batchInsert(records);
        log.info("Inserted {} new analysis records", insertedCount);

        return insertedCount;
    }
}
