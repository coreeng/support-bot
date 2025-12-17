package com.coreeng.supportbot.homepage;

import com.coreeng.supportbot.config.SupportInsightsProps;
import com.coreeng.supportbot.ticket.TicketStatus;
import com.coreeng.supportbot.ticket.TicketSummaryViewMapper;
import com.coreeng.supportbot.ticket.TicketsQuery;
import com.coreeng.supportbot.util.JsonMapper;
import com.google.common.collect.ImmutableList;
import com.slack.api.model.block.HeaderBlock;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import java.time.Instant;

class HomepageViewTest {
    private final JsonMapper jsonMapper = new JsonMapper();

    @Test
    public void testStateSerialization() {
        HomepageView.State state = HomepageView.State.getDefault();
        String serialized = jsonMapper.toJsonString(state);
        HomepageView.State deserialized = jsonMapper.fromJsonString(serialized, HomepageView.State.class);
        assertEquals(state, deserialized);
    }

    @Test
    public void shouldReturnExpectedTicketsQuery() {
        HomepageView.State state = HomepageView.State.builder()
                .filter(HomepageFilter.builder()
                        .escalationTeam("team-a")
                        .impact("production blocking")
                        .status(TicketStatus.opened)
                        .tags(ImmutableList.of("tag-a", "tag-b"))
                        .build())
                .build();

        TicketsQuery ticketsQuery = state.toTicketsQuery();

        assertThat(ticketsQuery.impacts().size()).isEqualTo(1);
        assertThat(ticketsQuery.impacts().getFirst()).isEqualTo("production blocking");
        assertThat(ticketsQuery.escalationTeam()).isEqualTo("team-a");
        assertThat(ticketsQuery.tags().size()).isEqualTo(2);
        assertThat(ticketsQuery.tags().get(0)).isEqualTo("tag-a");
        assertThat(ticketsQuery.tags().get(1)).isEqualTo("tag-b");
        assertThat(ticketsQuery.status()).isEqualTo(TicketStatus.opened);
    }

    @Test
    void supportInsightsPropsHandlesNullDashboards() {
        SupportInsightsProps props = new SupportInsightsProps(null);
        assertThat(props.dashboards()).isEqualTo(java.util.List.of());
    }

    @Test
    void renderSupportInsightsNoDashboards() {
        // given
        SupportInsightsProps props = new SupportInsightsProps(null);
        HomepageViewMapper mapper = new HomepageViewMapper(
            new TicketSummaryViewMapper(jsonMapper),
            jsonMapper,
            props
        );
        HomepageView homepage = HomepageView.builder()
            .tickets(ImmutableList.of())
            .page(0)
            .totalPages(1)
            .totalTickets(20)
            .channelId("C123")
            .timestamp(Instant.now())
            .state(HomepageView.State.getDefault())
            .build();

        // when
        var view = mapper.render(homepage);

        // then
        boolean hasInsightsHeader = view.renderBlocks().stream()
            .filter(block -> block instanceof HeaderBlock)
            .map(block -> (HeaderBlock) block)
            .anyMatch(header -> header.getText().getText().contains("Useful Links"));

        assertThat(hasInsightsHeader).isFalse();
    }

    @Test
    void renderSupportInsightsDashboardsPresent() {
        // given
        var dashboards = ImmutableList.of(
          new SupportInsightsProps.Dashboard("Weekly Trends", "https://grafana.example.com/weekly", "Weekly overview"),
          new SupportInsightsProps.Dashboard("Escalations", "https://grafana.example.com/escalations", null)
        );
        SupportInsightsProps props = new SupportInsightsProps(dashboards);
        HomepageViewMapper mapper = new HomepageViewMapper(
            new TicketSummaryViewMapper(jsonMapper),
            jsonMapper,
            props
        );
        HomepageView homepage = HomepageView.builder()
            .tickets(ImmutableList.of())
            .page(0)
            .totalPages(1)
            .totalTickets(20)
            .channelId("C123")
            .timestamp(Instant.now())
            .state(HomepageView.State.getDefault())
            .build();

        // when
        var view = mapper.render(homepage);

        // then
        boolean hasInsightsHeader = view.renderBlocks().stream()
            .filter(block -> block instanceof HeaderBlock)
            .map(block -> (HeaderBlock) block)
            .anyMatch(header -> header.getText().getText().contains("Useful Links"));

        assertThat(hasInsightsHeader).isTrue();
        assertThat(view.renderBlocks().toString()).contains("Weekly Trends", "Weekly overview", "Escalations");
}

}