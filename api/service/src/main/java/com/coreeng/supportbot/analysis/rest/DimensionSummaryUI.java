package com.coreeng.supportbot.analysis.rest;

import java.time.Instant;
import java.util.List;

/**
 * Response model for dimension summaries (categories, drivers, etc.) with example queries.
 *
 * @param name The dimension name (e.g., category or driver)
 * @param coveragePercentage Fixed value of 100
 * @param queryCount Number of queries in this dimension
 * @param queries List of query summaries
 */
public record DimensionSummaryUI(String name, int coveragePercentage, long queryCount, List<QuerySummary> queries) {
    /**
     * Individual query summary.
     *
     * @param text The summary text
     * @param timestamp The query timestamp (serialized as ISO-8601 by Jackson)
     * @param ticketId The related ticket ID (string to match the UI convention for safe JSON number handling)
     */
    public record QuerySummary(String text, Instant timestamp, String ticketId) {}
}
