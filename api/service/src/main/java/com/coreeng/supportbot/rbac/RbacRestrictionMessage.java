package com.coreeng.supportbot.rbac;

import com.coreeng.supportbot.slack.client.SlackMessage;
import com.google.common.collect.ImmutableList;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.block.SectionBlock;
import com.slack.api.model.block.composition.MarkdownTextObject;

public record RbacRestrictionMessage() implements SlackMessage {
    @Override
    public String getText() {
        return "Access Restricted - This action is only available to support team members.";
    }

    @Override
    public ImmutableList<LayoutBlock> renderBlocks() {
        return ImmutableList.of(SectionBlock.builder()
                .text(MarkdownTextObject.builder()
                        .text(
                                ":red_circle: *Access Restricted*\nThis action is only available to support team members.")
                        .build())
                .build());
    }
}
