package com.coreeng.supportbot.escalation.command;

import com.coreeng.supportbot.slack.client.SlackMessage;
import com.google.common.collect.ImmutableList;
import com.slack.api.model.Attachment;

import java.util.List;

import static com.slack.api.model.block.Blocks.*;
import static com.slack.api.model.block.composition.BlockCompositions.plainText;
import static com.slack.api.model.block.element.BlockElements.button;

public class CommandButtonsMessage implements SlackMessage {
    @Override
    public ImmutableList<Attachment> renderAttachments() {
        return ImmutableList.of(Attachment.builder()
            .fallback("Command options")
            .blocks(asBlocks(
                section(s -> s
                    .text(plainText(t -> t
                        .text("Hello there! What would like to do?")
                    ))
                ),
                actions(List.of(
                    button(b -> b
                        .actionId(CommandButton.escalate.actionId())
                        .text(plainText("Escalate"))
                    )
                ))
            ))
            .build());
    }
}
