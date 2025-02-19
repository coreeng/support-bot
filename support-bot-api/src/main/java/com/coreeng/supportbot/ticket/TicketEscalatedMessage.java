package com.coreeng.supportbot.ticket;

import com.coreeng.supportbot.slack.client.SlackMessage;
import com.google.common.collect.ImmutableList;
import com.slack.api.model.block.LayoutBlock;

import static com.slack.api.model.block.Blocks.section;
import static com.slack.api.model.block.composition.BlockCompositions.markdownText;

public record TicketEscalatedMessage(
    String escalationThreadPermalink,
    String teamName
) implements SlackMessage {
    @Override
    public String getText() {
        return "Ticket is escalated";
    }

    @Override
    public ImmutableList<LayoutBlock> renderBlocks() {
        return ImmutableList.of(section(s -> s
            .text(markdownText(messageText()))
        ));
    }

    private String messageText() {
        return "Escalated ticket to team " +
            teamName +
            "\n<" +
            escalationThreadPermalink +
            "|View Thread>";
    }
}
