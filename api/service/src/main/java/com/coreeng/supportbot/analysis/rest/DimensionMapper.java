package com.coreeng.supportbot.analysis.rest;

import com.coreeng.supportbot.analysis.AnalysisRepository.DimensionSummary;
import com.coreeng.supportbot.slack.MessageTs;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Mapper to transform DimensionSummary records to DimensionSummaryUI.
 */
@Component
public class DimensionMapper {

    private static final int COVERAGE_PERCENTAGE = 100;

    /**
     * Transform a list of DimensionSummary records to DimensionSummaryUI.
     * Groups summaries by dimension (category, driver, etc.) and creates the response structure.
     *
     * @param dimensionSummaries List of dimension summaries from repository
     * @return List of DimensionSummaryUI with grouped summaries
     */
    public List<DimensionSummaryUI> mapToUI(List<DimensionSummary> dimensionSummaries) {
        Map<String, List<DimensionSummary>> groupedByDimension =
                dimensionSummaries.stream().collect(Collectors.groupingBy(DimensionSummary::dimension));

        return groupedByDimension.entrySet().stream()
                .map(entry -> {
                    String dimension = entry.getKey();
                    List<DimensionSummary> summaries = entry.getValue();
                    long queryCount =
                            summaries.isEmpty() ? 0 : summaries.getFirst().queryCount();

                    List<DimensionSummaryUI.QuerySummary> queries = summaries.stream()
                            .map(summary -> new DimensionSummaryUI.QuerySummary(
                                    summary.summary(),
                                    MessageTs.of(summary.queryTs()).getDate().toString(),
                                    String.valueOf(summary.ticketId().id())))
                            .toList();

                    return new DimensionSummaryUI(dimension, COVERAGE_PERCENTAGE, queryCount, queries);
                })
                .sorted(Comparator.comparingLong(DimensionSummaryUI::queryCount).reversed())
                .toList();
    }
}
