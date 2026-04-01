package com.coreeng.supportbot.analysis.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.coreeng.supportbot.analysis.AnalysisRepository.DimensionSummary;
import com.coreeng.supportbot.ticket.TicketId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DimensionMapperTest {

    private DimensionMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new DimensionMapper();
    }

    @Test
    void mapToUI_withSummaries_includesTimestampAndTicketId() {
        List<DimensionSummary> summaries = List.of(
                new DimensionSummary(
                        "Networking", 10, "DNS resolution failures", new TicketId(101), "1740988800.000001"),
                new DimensionSummary(
                        "Networking", 10, "Ingress timeout issues", new TicketId(102), "1740988800.000002"));

        List<DimensionSummaryUI> result = mapper.mapToUI(summaries);

        assertEquals(1, result.size());
        List<DimensionSummaryUI.QuerySummary> queries = result.get(0).queries();
        assertEquals("DNS resolution failures", queries.get(0).text());
        assertEquals("1740988800.000001", queries.get(0).timestamp());
        assertEquals("101", queries.get(0).ticketId());
        assertEquals("Ingress timeout issues", queries.get(1).text());
        assertEquals("1740988800.000002", queries.get(1).timestamp());
        assertEquals("102", queries.get(1).ticketId());
    }

    @Test
    void mapToUI_withMultipleDimensions_groupsAndSortsByCountDescending() {
        List<DimensionSummary> summaries = List.of(
                new DimensionSummary("Secrets", 5, "Vault access denied", new TicketId(201), "1740988800.000003"),
                new DimensionSummary(
                        "Networking", 20, "DNS resolution failures", new TicketId(202), "1740988800.000004"),
                new DimensionSummary(
                        "Networking", 20, "Ingress timeout issues", new TicketId(203), "1740988800.000005"));

        List<DimensionSummaryUI> result = mapper.mapToUI(summaries);

        assertEquals(2, result.size());
        assertEquals("Networking", result.get(0).name());
        assertEquals(20, result.get(0).queryCount());
        assertEquals(2, result.get(0).queries().size());
        assertEquals("1740988800.000004", result.get(0).queries().get(0).timestamp());
        assertEquals("202", result.get(0).queries().get(0).ticketId());
        assertEquals("Secrets", result.get(1).name());
        assertEquals(5, result.get(1).queryCount());
        assertEquals(1, result.get(1).queries().size());
    }

    @Test
    void mapToUI_withEmptyInput_returnsEmptyList() {
        List<DimensionSummary> summaries = List.of();

        List<DimensionSummaryUI> result = mapper.mapToUI(summaries);

        assertTrue(result.isEmpty());
    }
}
