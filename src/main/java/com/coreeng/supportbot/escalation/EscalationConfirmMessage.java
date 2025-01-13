package com.coreeng.supportbot.escalation;

import com.coreeng.supportbot.slack.client.SlackMessage;
import com.google.common.collect.ImmutableList;
import com.slack.api.model.Attachment;

import java.util.List;

import static com.slack.api.model.block.Blocks.*;
import static com.slack.api.model.block.composition.BlockCompositions.plainText;
import static com.slack.api.model.block.element.BlockElements.button;

public class EscalationConfirmMessage implements SlackMessage {
    @Override
    public ImmutableList<Attachment> renderAttachments() {
        return ImmutableList.of(Attachment.builder()
            .fallback("Confirm escalation")
            .blocks(asBlocks(
                section(s -> s
                    .text(plainText("Please, make sure the above details are correct."))
                ),
                actions(List.of(
                    button(b -> b
                        .actionId(EscalationOperation.confirm.actionId())
                        .text(plainText("Confirm"))
                    )
                ))
            ))
            .build());
    }
}
