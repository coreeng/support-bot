package com.coreeng.supportbot.ticket;

import com.coreeng.supportbot.slack.client.SlackMessage;
import com.google.common.collect.ImmutableList;
import com.slack.api.model.Attachment;
import com.slack.api.model.block.LayoutBlock;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static com.slack.api.model.block.Blocks.*;
import static com.slack.api.model.block.composition.BlockCompositions.markdownText;
import static com.slack.api.model.block.composition.BlockCompositions.plainText;
import static com.slack.api.model.block.element.BlockElements.button;
import static java.lang.String.format;

public record TicketCreatedMessage(
    TicketId ticketId,
    TicketStatus status,
    Instant statusChangedDate,
    DateTimeFormatter dateFormatter
) implements SlackMessage {
    private final static String redHex = "#ff000d";
    private final static String greenHex = "#00ff00";

    @Override
    public String getText() {
        return "Ticket Created: ID-" + ticketId.id();
    }

    @Override
    public ImmutableList<LayoutBlock> renderBlocks() {
        return ImmutableList.of(
            section(s -> s.
                text(markdownText(format("*Ticket Created*: `ID-%d`", ticketId.id())))
            )
        );
    }

    @Override
    public ImmutableList<Attachment> renderAttachments() {
        // TODO: what timezone should be used?
        String title = status.renderMessage(dateFormatter.format(statusChangedDate.atOffset(ZoneOffset.UTC)));
        List<LayoutBlock> blocks = asBlocks(
            divider(),
            section(s -> s.text(plainText(title))),
            actions(List.of(
                button(b -> b
                    .actionId(TicketOperation.toggle.publicName())
                    .text(plainText(
                        status == TicketStatus.unresolved
                            ? "Close Ticket"
                            : "Reopen Ticket"
                    ))
                    .style(
                        status == TicketStatus.unresolved
                            ? "danger"
                            : "primary"
                    ))
            )),
            actions(List.of(
                button(b -> b
                    .actionId(TicketOperation.summaryView.publicName())
                    .text(plainText("Full Summary")))
            )),
            context(List.of(
                plainText(t -> t
                    .text(":pushpin: Options above supplied for Support engineers. Please ignore...")
                    .emoji(true))
            ))
        );
        return ImmutableList.of(Attachment.builder()
            .fallback(title)
            .blocks(blocks)
            .color(status == TicketStatus.unresolved
                ? greenHex
                : redHex)
            .build());
    }
}
