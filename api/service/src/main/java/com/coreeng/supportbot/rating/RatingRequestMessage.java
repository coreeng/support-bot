package com.coreeng.supportbot.ticket;

import com.coreeng.supportbot.slack.client.SlackMessage;
import com.google.common.collect.ImmutableList;
import com.slack.api.model.block.LayoutBlock;

import static com.slack.api.model.block.Blocks.*;
import static com.slack.api.model.block.composition.BlockCompositions.markdownText;
import static com.slack.api.model.block.composition.BlockCompositions.plainText;
import static com.slack.api.model.block.element.BlockElements.button;

public record RatingRequestMessage(
    TicketId ticketId
) implements SlackMessage {

    @Override
    public String getText() {
        return "How was your support experience?";
    }

    @Override
    public ImmutableList<LayoutBlock> renderBlocks() {
        return ImmutableList.of(
            section(s -> s
                .text(markdownText(
                    "*How was your support experience?* \n" +
                    "_Your feedback helps us improve our service_"
                ))
            ),
            actions(ImmutableList.of(
                button(b -> b
                    .actionId("rating_submit_" + ticketId.id() + "_1")
                    .text(plainText("⭐ (1)"))
                    .value("1")
                ),
                button(b -> b
                    .actionId("rating_submit_" + ticketId.id() + "_2")
                    .text(plainText("⭐⭐ (2)"))
                    .value("2")
                ),
                button(b -> b
                    .actionId("rating_submit_" + ticketId.id() + "_3")
                    .text(plainText("⭐⭐⭐ (3)"))
                    .value("3")
                ),
                button(b -> b
                    .actionId("rating_submit_" + ticketId.id() + "_4")
                    .text(plainText("⭐⭐⭐⭐ (4)"))
                    .value("4")
                    .style("primary")
                ),
                button(b -> b
                    .actionId("rating_submit_" + ticketId.id() + "_5")
                    .text(plainText("⭐⭐⭐⭐⭐ (5)"))
                    .value("5")
                    .style("primary")
                )
            ))
        );
    }
}
