package com.coreeng.supportbot.analysis;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for querying and importing analysis results.
 *
 * <p>This service provides business logic for:
 * <ul>
 *   <li>Retrieving aggregated analysis data (top drivers, categories with examples)</li>
 *   <li>Importing bulk analysis data (e.g., from external sources or backfills)</li>
 * </ul>
 *
 * <p>For LLM-powered analysis orchestration, see {@link AnalysisService}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AnalysisResultsService {

    private final AnalysisRepository analysisRepository;

    /**
     * Gets the top 5 "Knowledge Gap" categories with up to 5 example summaries for each.
     *
     * @return List of dimension summaries ordered by count (descending)
     */
    public List<AnalysisRepository.DimensionSummary> getKnowledgeGapCategoriesWithSummaries() {
        return analysisRepository.getKnowledgeGapCategoriesWithSummaries();
    }

    /**
     * Gets the top 5 drivers with up to 5 example summaries for each.
     *
     * @return List of dimension summaries ordered by count (descending)
     */
    public List<AnalysisRepository.DimensionSummary> getDriversWithSummaries() {
        return analysisRepository.getDriversWithSummaries();
    }

    /**
     * Imports analysis data from a list of records.
     * Inserts new records or updates existing records based on {@code ticket_id}.
     *
     * <p>This is typically used for bulk imports or backfills, not for LLM-generated analysis
     * (which uses {@link AnalysisService}).
     *
     * @param records List of analysis records to import
     * @return Number of records affected
     */
    @Transactional
    public int importAnalysisData(List<AnalysisRepository.AnalysisRecord> records) {
        log.info("Starting analysis data import with {} records", records.size());

        int affectedCount = analysisRepository.upsert(records);
        log.info("Upserted {} analysis records", affectedCount);

        return affectedCount;
    }
}
