package com.coreeng.supportbot.escalation;

import com.coreeng.supportbot.EnumerationValue;
import com.coreeng.supportbot.slack.client.SlackMessage;
import com.google.common.collect.ImmutableList;
import com.slack.api.model.Attachment;

import static com.slack.api.model.block.Blocks.asBlocks;
import static com.slack.api.model.block.Blocks.section;
import static com.slack.api.model.block.composition.BlockCompositions.plainText;

public record EscalationConfirmedMessage(
    EscalationId id,
    EnumerationValue topic,
    EnumerationValue team
) implements SlackMessage {
    @Override
    public ImmutableList<Attachment> renderAttachments() {
        String message = renderMessage();
        return ImmutableList.of(Attachment.builder()
            .fallback(message)
            .blocks(asBlocks(
                section(s -> s
                    .text(plainText(message))
                )
            ))
            .build());
    }

    private String renderMessage() {
        return """
            Thank you for your submission
            This issue is now being tracked under:
            ID:\s""" +
            id.id() +
            "\nTopic: " +
            topic.name() +
            "\nTeam: " +
            team.name() +
            "\n\nYou can now escalate the query to #" +
            team.code();
    }
}
