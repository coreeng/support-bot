package com.coreeng.supportbot.analysis.rest;

import com.coreeng.supportbot.analysis.AnalysisRepository.DimensionSummary;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Mapper to transform DimensionSummary records to DimensionSummaryUI.
 */
@Component
public class DimensionMapper {

    private static final String SLACK_LINK = "https://some.slack.com/archives/ARCACLESD/p1770295016842609";
    private static final int COVERAGE_PERCENTAGE = 100;

    /**
     * Transform a list of DimensionSummary records to DimensionSummaryUI.
     * Groups summaries by dimension (category, driver, etc.) and creates the response structure.
     *
     * @param dimensionSummaries List of dimension summaries from repository
     * @return List of DimensionSummaryUI with grouped summaries
     */
    public List<DimensionSummaryUI> mapToUI(List<DimensionSummary> dimensionSummaries) {
        // Group summaries by dimension (category)
        Map<String, List<DimensionSummary>> groupedByDimension =
                dimensionSummaries.stream().collect(Collectors.groupingBy(DimensionSummary::dimension));

        // Transform each dimension group into a response object
        return groupedByDimension.entrySet().stream()
                .map(entry -> {
                    String dimension = entry.getKey();
                    List<DimensionSummary> summaries = entry.getValue();

                    // Get query count from the first summary (all have the same count for the same dimension)
                    long queryCount = summaries.isEmpty() ? 0 : summaries.get(0).queryCount();

                    // Map summaries to QuerySummary objects
                    List<DimensionSummaryUI.QuerySummary> queries = summaries.stream()
                            .map(summary -> new DimensionSummaryUI.QuerySummary(summary.summary(), SLACK_LINK))
                            .toList();

                    return new DimensionSummaryUI(dimension, COVERAGE_PERCENTAGE, queryCount, queries);
                })
                // Sort by query count descending to maintain top categories first
                .sorted((a, b) -> Long.compare(b.queryCount(), a.queryCount()))
                .toList();
    }
}
