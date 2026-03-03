package com.coreeng.supportbot.analysis.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.coreeng.supportbot.analysis.AnalysisRepository.DimensionSummary;
import com.coreeng.supportbot.slack.SlackException;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.coreeng.supportbot.slack.client.SlackGetMessageByTsRequest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DimensionMapperTest {

    private DimensionMapper mapper;
    private SlackClient slackClient;

    @BeforeEach
    void setUp() {
        slackClient = mock(SlackClient.class);
        mapper = new DimensionMapper(slackClient);
    }

    @Test
    void mapToUIResolvesQueryPermalinks() {
        when(slackClient.getPermalink(any(SlackGetMessageByTsRequest.class)))
                .thenReturn("https://slack.com/permalink-1", "https://slack.com/permalink-2");

        // given two summaries in the same dimension with different Slack query refs
        List<DimensionSummary> summaries = List.of(
                new DimensionSummary("Networking", 10, "DNS resolution failures", "C111", "1740988800.000001"),
                new DimensionSummary("Networking", 10, "Ingress timeout issues", "C222", "1740988800.000002"));

        // when mapping to UI
        List<DimensionSummaryUI> result = mapper.mapToUI(summaries);

        // then each query summary carries the resolved Slack permalink
        assertEquals(1, result.size());
        List<DimensionSummaryUI.QuerySummary> queries = result.get(0).queries();
        assertEquals("https://slack.com/permalink-1", queries.get(0).link());
        assertEquals("https://slack.com/permalink-2", queries.get(1).link());
    }

    @Test
    void mapToUIGroupsByDimensionAndSortsByCountDescending() {
        when(slackClient.getPermalink(any(SlackGetMessageByTsRequest.class)))
                .thenReturn(
                        "https://slack.com/permalink-1",
                        "https://slack.com/permalink-2",
                        "https://slack.com/permalink-3");

        // given summaries across two dimensions with different query counts
        List<DimensionSummary> summaries = List.of(
                new DimensionSummary("Secrets", 5, "Vault access denied", "C001", "1740988800.000003"),
                new DimensionSummary("Networking", 20, "DNS resolution failures", "C002", "1740988800.000004"),
                new DimensionSummary("Networking", 20, "Ingress timeout issues", "C003", "1740988800.000005"));

        // when mapping to UI
        List<DimensionSummaryUI> result = mapper.mapToUI(summaries);

        // then dimensions are grouped and the higher count appears first
        assertEquals(2, result.size());
        assertEquals("Networking", result.get(0).name());
        assertEquals(20, result.get(0).queryCount());
        assertEquals(2, result.get(0).queries().size());
        assertEquals("Secrets", result.get(1).name());
        assertEquals(5, result.get(1).queryCount());
        assertEquals(1, result.get(1).queries().size());
    }

    @Test
    void mapToUIReturnsNullLinkWhenPermalinkFails() {
        when(slackClient.getPermalink(any(SlackGetMessageByTsRequest.class)))
                .thenThrow(new SlackException(new RuntimeException("rate limited")));

        List<DimensionSummary> summaries =
                List.of(new DimensionSummary("Networking", 10, "DNS resolution failures", "C111", "1740988800.000001"));

        List<DimensionSummaryUI> result = mapper.mapToUI(summaries);

        assertEquals(1, result.size());
        assertEquals(1, result.get(0).queries().size());
        assertNull(result.get(0).queries().get(0).link());
    }

    @Test
    void mapToUIReturnsEmptyListForEmptyInput() {
        // given no summaries
        List<DimensionSummary> summaries = List.of();

        // when mapping to UI
        List<DimensionSummaryUI> result = mapper.mapToUI(summaries);

        // then the result is empty
        assertTrue(result.isEmpty());
    }
}
