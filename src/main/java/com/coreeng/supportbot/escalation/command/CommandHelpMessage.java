package com.coreeng.supportbot.escalation.command;

import com.coreeng.supportbot.slack.client.SlackMessage;
import com.google.common.collect.ImmutableList;
import com.slack.api.model.Attachment;

import static com.slack.api.model.block.Blocks.asBlocks;
import static com.slack.api.model.block.Blocks.section;
import static com.slack.api.model.block.composition.BlockCompositions.plainText;

public class CommandHelpMessage implements SlackMessage {
    private final static String helpMessage = """
        Available commands:
        resolve - Resolve the escalation""";

    @Override
    public ImmutableList<Attachment> renderAttachments() {
        return ImmutableList.of(Attachment.builder()
            .fallback(helpMessage)
            .blocks(asBlocks(
                section(s -> s
                    .text(plainText(t -> t.text(helpMessage)))
                )
            ))
            .build());
    }
}
