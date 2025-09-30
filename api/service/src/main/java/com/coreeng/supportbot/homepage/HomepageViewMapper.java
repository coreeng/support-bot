package com.coreeng.supportbot.homepage;

import com.coreeng.supportbot.escalation.Escalation;
import com.coreeng.supportbot.slack.client.SimpleSlackView;
import com.coreeng.supportbot.slack.client.SlackView;
import com.coreeng.supportbot.ticket.TicketOperation;
import com.coreeng.supportbot.ticket.TicketStatus;
import com.coreeng.supportbot.ticket.TicketSummaryViewInput;
import com.coreeng.supportbot.ticket.TicketSummaryViewMapper;
import com.coreeng.supportbot.util.JsonMapper;
import com.google.common.collect.ImmutableList;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.block.SectionBlock;
import com.slack.api.model.block.element.BlockElement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Nullable;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.slack.api.model.block.Blocks.*;
import static com.slack.api.model.block.composition.BlockCompositions.markdownText;
import static com.slack.api.model.block.composition.BlockCompositions.plainText;
import static com.slack.api.model.block.element.BlockElements.button;
import static java.lang.String.*;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

@Component
@RequiredArgsConstructor
@Slf4j
public class HomepageViewMapper {
    private final TicketSummaryViewMapper ticketSummaryViewMapper;
    private final JsonMapper jsonMapper;

    public SlackView render(HomepageView homepage) {
        ImmutableList.Builder<LayoutBlock> blocks = ImmutableList.builder();
        blocks.add(
            header(h -> h
                .text(plainText(":rocket: Admin Panel", true))
            ),
            section(s -> s
                .text(markdownText(lastUpdatedMessage(homepage)))
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
        Optional<SectionBlock> inquiringTeam = buildInquiringTeamSection(ticket);
        Optional<SectionBlock> escalationSection = buildEscalationSection(ticket);
        Optional<SectionBlock> statusSection = buildTicketStatusSection(ticket);
        List<LayoutBlock> blocks = new ArrayList<>(List.of(
            section(s -> s
                .text(markdownText(header))
                .accessory(button(b -> b
                    .actionId(TicketOperation.summaryView.actionId())
                    .text(plainText("Full Summary"))
                    .value(ticketSummaryViewMapper.createTriggerInput(new TicketSummaryViewInput(ticket.id())))
                ))
            ),
            section(s -> s.text(markdownText(format("*Impact*: %s",
                    ticket.impact() == null ? "Not Evaluated" : ticket.impact().label()))
             )),
            section(s -> s.text(markdownText(format("*Last Opened*: %s",
                    formatSlackDate(ticket.lastOpenedAt()))))
            )
        ));
    statusSection.ifPresent(blocks::add);
    inquiringTeam.ifPresent(blocks::add);
    escalationSection.ifPresent(blocks::add);
    blocks.add(divider());
    return ImmutableList.copyOf(blocks);
}
    private Optional<SectionBlock> buildTicketStatusSection(TicketView ticket) {
        return ticket.status() == TicketStatus.closed ?
            Optional.of(section(
                s-> s.fields(ImmutableList.of(
                    markdownText(
                        format("*Closed*: %s", formatSlackDate(requireNonNull(ticket.closedAt())))))
                    )
            )):
            Optional.empty();
    }

    private Optional<SectionBlock> buildEscalationSection(TicketView ticket) {
        return Optional.ofNullable(ticket.escalations())
            .filter(escalations -> !escalations.isEmpty())
            .map(escalations -> section(s -> {
                ImmutableList<String> teams = escalations.stream()
                    .map(Escalation::team)
                    .collect(toImmutableList());
                return s.fields(ImmutableList.of(
                    markdownText(format("*Escalated to*: %s :rocket:", join(", ", teams)))
                ));
            }));
    }

    private Optional<SectionBlock> buildInquiringTeamSection(TicketView ticket) {
        return Optional.ofNullable(ticket.inquiringTeam())
            .map(inquiringTeam -> section(s -> s.fields(ImmutableList.of(
                    markdownText(format("*Inquiring Team*: %s", inquiringTeam))
            ))));
    }

    private String lastUpdatedMessage(HomepageView homepage) {
        return "Last updated: " + formatSlackDate(homepage.timestamp());
    }

    private String viewDescriptionMessage(HomepageView homepage) {
        return "Handle all queries, including their escalations. "
                + "The bot is designed to assist support engineers in managing and escalating support queries within the <#"
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
        return blocks.build();
    }

    private String pageNumberMessage(HomepageView homepage) {
        return format("Page %d of %d", homepage.page() + 1, homepage.totalPages());
    }

    private String formatSlackDate(Instant instant) {
        return "<!date^" + instant.getEpochSecond() + "^{date_short_pretty} at {time}|" + instant.truncatedTo(ChronoUnit.MINUTES) + ">";
    }
}
