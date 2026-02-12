package com.coreeng.supportbot.analysis.rest;

import java.util.List;

/**
 * Response model for analysis data containing knowledge gaps and support areas.
 *
 * @param knowledgeGaps List of knowledge gap dimension summaries
 * @param supportAreas List of support area dimension summaries (drivers)
 */
public record AnalysisUI(List<DimensionSummaryUI> knowledgeGaps, List<DimensionSummaryUI> supportAreas) {}
