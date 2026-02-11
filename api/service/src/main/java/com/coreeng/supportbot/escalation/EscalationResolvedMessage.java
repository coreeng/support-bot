package com.coreeng.supportbot.escalation;

import static com.slack.api.model.block.Blocks.asBlocks;
import static com.slack.api.model.block.Blocks.section;
import static com.slack.api.model.block.composition.BlockCompositions.plainText;

import com.coreeng.supportbot.slack.client.SlackMessage;
import com.google.common.collect.ImmutableList;
import com.slack.api.model.Attachment;

public record EscalationResolvedMessage(EscalationId id) implements SlackMessage {
    @Override
    public ImmutableList<Attachment> renderAttachments() {
        String message = renderMessage();
        return ImmutableList.of(Attachment.builder()
                .fallback(message)
                .blocks(asBlocks(section(s -> s.text(plainText(message)))))
                .build());
    }

    private String renderMessage() {
        return "Thank you for you submission. Escalation " + id.id() + " has been resolved.";
    }
}
