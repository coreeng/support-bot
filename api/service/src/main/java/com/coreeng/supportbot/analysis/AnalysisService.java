package com.coreeng.supportbot.analysis;

import java.util.List;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * Service for analysis data operations.
 */
@Service
@RequiredArgsConstructor
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
}

