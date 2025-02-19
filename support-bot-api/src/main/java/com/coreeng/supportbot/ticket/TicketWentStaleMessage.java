package com.coreeng.supportbot.ticket;

import com.coreeng.supportbot.slack.client.SlackMessage;
import com.google.common.collect.ImmutableList;
import com.slack.api.model.block.LayoutBlock;

import static com.slack.api.model.block.Blocks.section;
import static com.slack.api.model.block.composition.BlockCompositions.markdownText;

public record TicketWentStaleMessage(
    String authorId,
    String supportGroupId
) implements SlackMessage {
    @Override
    public String getText() {
//        return "Ticket went stale";
        return messageText();
    }

    @Override
    public ImmutableList<LayoutBlock> renderBlocks() {
        return ImmutableList.of(section(s -> s
            .text(markdownText(messageText()))
        ));
    }

    private String messageText() {
        return ":warning: Ticket went stale, because there was no interactions with it for a long time and it's not closed.\n" +
            "Are any other actions required or it can be closed?\n" +
            "<@" + authorId + "> <!subteam^" + supportGroupId + ">";
    }
}
