package com.coreeng.supportbot.homepage;

import com.coreeng.supportbot.slack.client.SimpleSlackView;
import com.coreeng.supportbot.slack.client.SlackView;
import com.coreeng.supportbot.ticket.TicketOperation;
import com.coreeng.supportbot.ticket.TicketStatus;
import com.coreeng.supportbot.ticket.TicketSummaryViewInput;
import com.coreeng.supportbot.ticket.TicketSummaryViewMapper;
import com.coreeng.supportbot.util.JsonMapper;
import com.coreeng.supportbot.util.RelativeDateFormatter;
import com.google.common.collect.ImmutableList;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.block.composition.TextObject;
import com.slack.api.model.block.element.BlockElement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Nullable;
import java.util.List;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.slack.api.model.block.Blocks.*;
import static com.slack.api.model.block.Blocks.context;
import static com.slack.api.model.block.composition.BlockCompositions.markdownText;
import static com.slack.api.model.block.composition.BlockCompositions.plainText;
import static com.slack.api.model.block.element.BlockElements.button;
import static java.lang.String.format;

@Component
@RequiredArgsConstructor
@Slf4j
public class HomepageViewMapper {
    private final TicketSummaryViewMapper ticketSummaryViewMapper;
    private final RelativeDateFormatter dateFormatter;
    private final JsonMapper jsonMapper;

    public SlackView render(HomepageView homepage) {
        ImmutableList.Builder<LayoutBlock> blocks = ImmutableList.builder();
        blocks.add(
            header(h -> h
                .text(plainText(":rocket: Admin Panel", true))
            ),
            section(s -> s
                .text(plainText(lastUpdatedMessage(homepage)))
                .accessory(button(b -> b
                    .actionId(HomepageOperation.refresh.actionId())
                    .text(plainText("Refresh"))
                ))
            ),
            divider(),
            header(h -> h
                .text(plainText(":ticket: Support Query Tickets Summary", true))
            ),
            section(s -> s
                .text(markdownText(viewDescriptionMessage(homepage)))
                .accessory(button(b -> b
                    .actionId(HomepageOperation.filter.actionId())
                    .text(plainText("Filter"))
                ))
            ),
            context(List.of(
                plainText("Total tickets: " + homepage.totalTickets())
            )),
            divider()
        );
        blocks.addAll(
            homepage.tickets().stream()
                .flatMap(ticket -> renderTicket(ticket).stream())
                .collect(toImmutableList())
        );
        blocks.addAll(renderBottomBlock(homepage));
        return new SimpleSlackView(
            blocks.build(),
            privateMetadata(homepage)
        );
    }

    public HomepageView.State parseMetadataOrDefault(@Nullable String json) {
        if (json == null) {
            return HomepageView.State.getDefault();
        }
        try {
            return jsonMapper.fromJsonString(json, HomepageView.State.class);
        } catch (Exception e) {
            log.warn("Couldn't parse homepage metadata", e);
            throw e;
        }
    }

    private String privateMetadata(HomepageView homepage) {
        return jsonMapper.toJsonString(homepage.state());
    }

    private List<LayoutBlock> renderTicket(TicketView ticket) {
        String header = format(":%s: %s - Ticket <%s|%s>",
            ticket.status().emoji(),
            ticket.status().label(),
            ticket.queryPermalink(),
            ticket.id().render()
        );
        return List.of(
            section(s -> s
                .text(markdownText(header))
                .accessory(button(b -> b
                    .actionId(TicketOperation.summaryView.actionId())
                    .text(plainText("Full Summary"))
                    .value(ticketSummaryViewMapper.createTriggerInput(new TicketSummaryViewInput(ticket.id())))
                ))
            ),
            section(s -> {
                    ImmutableList.Builder<TextObject> fields = ImmutableList.builder();
                    fields.add(
                        markdownText(format("*Impact*: %s", ticket.impact() == null ? "Not Evaluated" : ticket.impact().name())),
                        // empty field so that UI is aligned this way:
                        // impact
                        // lastOpened ----- closed
                        markdownText(" "),
                        markdownText(format("*Last Opened*: %s", dateFormatter.format(ticket.lastOpenedAt())))
                    );
                    if (ticket.status() == TicketStatus.closed) {
                        fields.add(
                            markdownText(format("*Closed*: %s", dateFormatter.format(ticket.closedAt())))
                        );
                    }
                    return s.fields(fields.build());
                }
            ),
            divider()
        );
    }

    private String lastUpdatedMessage(HomepageView homepage) {
        return "Last updated: " + dateFormatter.format(homepage.timestamp());
    }

    private String viewDescriptionMessage(HomepageView homepage) {
        return "Manage existing/closed queries including their escalations and follow up procedures."
            + "The CommunityBot is purposed to help support engineers manage and escalate support queries for the <#"
            + homepage.channelId()
            + "> channel.";
    }

    private List<LayoutBlock> renderBottomBlock(HomepageView homepage) {
        ImmutableList.Builder<BlockElement> pageButtons = ImmutableList.builder();
        if (homepage.page() > 0) {
            pageButtons.add(
                button(b -> b
                    .actionId(HomepageOperation.previousPage.actionId())
                    .text(plainText("Previous Page"))
                    .value("previous")
                )
            );
        }
        if (homepage.page() < homepage.totalPages() - 1) {
            pageButtons.add(
                button(b -> b
                    .actionId(HomepageOperation.nextPage.actionId())
                    .text(plainText("Next Page"))
                    .value("next")
                )
            );
        }
        ImmutableList.Builder<LayoutBlock> blocks = ImmutableList.builder();
        blocks.add(section(s -> s
            .text(plainText(pageNumberMessage(homepage)))
        ));
        if (!pageButtons.build().isEmpty()) {
            blocks.add(actions(pageButtons.build()));
        }
        blocks.add(context(c -> c
            .elements(List.of(
                plainText(":pushpin: This is a message only visible by Core-Community admins.", true)
            ))
        ));
        return blocks.build();
    }

    private String pageNumberMessage(HomepageView homepage) {
        return format("Page %d of %d", homepage.page() + 1, homepage.totalPages());
    }
}
