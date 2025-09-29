package com.coreeng.supportbot.homepage;

import com.coreeng.supportbot.config.SupportInsightsProps;
import com.coreeng.supportbot.slack.client.SlackView;
import com.coreeng.supportbot.ticket.TicketSummaryViewMapper;
import com.coreeng.supportbot.util.JsonMapper;
import com.google.common.collect.ImmutableList;
import com.slack.api.model.block.HeaderBlock;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.block.SectionBlock;
import com.slack.api.model.block.composition.MarkdownTextObject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HomepageViewMapperTest {
    private final JsonMapper jsonMapper = new JsonMapper();
    private final TicketSummaryViewMapper ticketSummaryViewMapper =
        new TicketSummaryViewMapper(jsonMapper);

    @Test
    void renderShouldIncludeSupportInsightsWhenConfigured() {
        SupportInsightsProps props = new SupportInsightsProps(List.of(
            new SupportInsightsProps.Dashboard("Weekly Trends", "https://grafana.example.com/weekly", "Weekly overview"),
            new SupportInsightsProps.Dashboard("Escalations", "https://grafana.example.com/escalations", null)
        ));

        HomepageViewMapper mapper = new HomepageViewMapper(
            ticketSummaryViewMapper,
            jsonMapper,
            props
        );

        SlackView slackView = mapper.render(emptyHomepageView());
        ImmutableList<LayoutBlock> blocks = slackView.renderBlocks();

        assertThat(blocks.stream()
            .filter(HeaderBlock.class::isInstance)
            .map(HeaderBlock.class::cast)
            .map(header -> header.getText().getText())
            .anyMatch(text -> text.contains("Support Insights")))
            .isTrue();

        assertThat(blocks.stream()
            .filter(SectionBlock.class::isInstance)
            .map(SectionBlock.class::cast)
            .flatMap(section -> section.getFields() == null
                ? java.util.stream.Stream.empty()
                : section.getFields().stream())
            .filter(MarkdownTextObject.class::isInstance)
            .map(MarkdownTextObject.class::cast)
            .map(MarkdownTextObject::getText)
            .anyMatch(text -> text.contains("<https://grafana.example.com/weekly|Weekly Trends>")))
            .isTrue();
    }

    @Test
    void renderShouldSkipSupportInsightsWhenNotConfigured() {
        SupportInsightsProps props = new SupportInsightsProps(List.of());
        HomepageViewMapper mapper = new HomepageViewMapper(
            ticketSummaryViewMapper,
            jsonMapper,
            props
        );

        SlackView slackView = mapper.render(emptyHomepageView());
        ImmutableList<LayoutBlock> blocks = slackView.renderBlocks();

        assertThat(blocks.stream()
            .filter(HeaderBlock.class::isInstance)
            .map(HeaderBlock.class::cast)
            .noneMatch(header -> header.getText().getText().contains("Support Insights")))
            .isTrue();
    }

    private HomepageView emptyHomepageView() {
        return HomepageView.builder()
            .tickets(ImmutableList.of())
            .page(0)
            .totalPages(1)
            .totalTickets(0)
            .channelId("C123")
            .timestamp(Instant.parse("2024-01-01T00:00:00Z"))
            .state(HomepageView.State.getDefault())
            .build();
    }
}
