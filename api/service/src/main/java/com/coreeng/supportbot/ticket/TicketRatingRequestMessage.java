package com.coreeng.supportbot.ticket;

import com.coreeng.supportbot.slack.client.SlackMessage;
import com.google.common.collect.ImmutableList;
import com.slack.api.model.block.LayoutBlock;

import static com.slack.api.model.block.Blocks.*;
import static com.slack.api.model.block.composition.BlockCompositions.markdownText;
import static com.slack.api.model.block.composition.BlockCompositions.plainText;
import static com.slack.api.model.block.element.BlockElements.button;

public record TicketRatingRequestMessage(
    TicketId ticketId
) implements SlackMessage {

    @Override
    public String getText() {
        return String.format("Please rate your experience with ticket %s", ticketId);
    }

    @Override
    public ImmutableList<LayoutBlock> renderBlocks() {
        return ImmutableList.of(
            section(s -> s
                .text(markdownText(String.format(
                    "🎯 *Please rate your experience with ticket %s*\n\n" +
                    "Your feedback helps us improve our support! Please select a rating:\n\n" +
                    "Thank you for using our support system! 🙏",
                    ticketId
                )))
            ),
            actions(ImmutableList.of(
                button(b -> b
                    .actionId("rating_submit_" + ticketId + "_1")
                    .text(plainText("⭐"))
                    .value("1")
                    .style("primary")
                ),
                button(b -> b
                    .actionId("rating_submit_" + ticketId + "_2")
                    .text(plainText("⭐⭐"))
                    .value("2")
                    .style("primary")
                ),
                button(b -> b
                    .actionId("rating_submit_" + ticketId + "_3")
                    .text(plainText("⭐⭐⭐"))
                    .value("3")
                    .style("primary")
                ),
                button(b -> b
                    .actionId("rating_submit_" + ticketId + "_4")
                    .text(plainText("⭐⭐⭐⭐"))
                    .value("4")
                    .style("primary")
                ),
                button(b -> b
                    .actionId("rating_submit_" + ticketId + "_5")
                    .text(plainText("⭐⭐⭐⭐⭐"))
                    .value("5")
                    .style("primary")
                )
            ))
        );
    }
}
