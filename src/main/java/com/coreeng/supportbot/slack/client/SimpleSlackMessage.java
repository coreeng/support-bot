package com.coreeng.supportbot.slack.client;

import com.google.common.collect.ImmutableList;
import com.slack.api.model.Attachment;
import com.slack.api.model.block.LayoutBlock;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SimpleSlackMessage implements SlackMessage {
    @Builder.Default
    private String text = "";
    @Builder.Default
    private ImmutableList<LayoutBlock> blocks = ImmutableList.of();
    @Builder.Default
    private ImmutableList<Attachment> attachments = ImmutableList.of();

    @Override
    public String getText() {
        return text;
    }

    @Override
    public ImmutableList<LayoutBlock> renderBlocks() {
        return blocks;
    }

    @Override
    public ImmutableList<Attachment> renderAttachments() {
        return attachments;
    }
}
