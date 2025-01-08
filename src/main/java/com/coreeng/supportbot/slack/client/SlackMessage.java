package com.coreeng.supportbot.slack.client;

import com.google.common.collect.ImmutableList;
import com.slack.api.model.Attachment;
import com.slack.api.model.block.LayoutBlock;

public interface SlackMessage {
    /**
     * @return Brief description of the message
     */
    default String getText() {
        return "";
    }

    default ImmutableList<LayoutBlock> renderBlocks() {
        return ImmutableList.of();
    }

    default ImmutableList<Attachment> renderAttachments() {
        return ImmutableList.of();
    }
}
