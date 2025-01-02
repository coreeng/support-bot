package com.coreeng.supportbot.slack.client;

import com.slack.api.model.Attachment;
import com.slack.api.model.block.LayoutBlock;

import java.util.List;

public interface SlackMessage {
    /**
     * @return Brief description of the message
     */
    String getText();

    default List<LayoutBlock> renderBlocks() {
        return List.of();
    }

    default List<Attachment> renderAttachments() {
        return List.of();
    }
}
