package com.coreeng.supportbot.analysis.rest;

import com.coreeng.supportbot.analysis.AnalysisService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for analysis data.
 */
@RestController
@RequestMapping("/summary-data/results")
@RequiredArgsConstructor
public class AnalysisController {

    private final AnalysisService analysisService;
    private final DimensionMapper dimensionMapper;

    /**
     * Get analysis data including knowledge gaps and support areas.
     *
     * @return AnalysisUI with knowledge gaps and support areas
     */
    @GetMapping
    public AnalysisUI getAnalysis() {
        List<DimensionSummaryUI> knowledgeGaps =
                dimensionMapper.mapToUI(analysisService.getKnowledgeGapCategoriesWithSummaries());

        List<DimensionSummaryUI> supportAreas = dimensionMapper.mapToUI(analysisService.getDriversWithSummaries());

        return new AnalysisUI(knowledgeGaps, supportAreas);
    }
}
