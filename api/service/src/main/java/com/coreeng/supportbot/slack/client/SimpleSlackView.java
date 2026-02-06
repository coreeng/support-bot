package com.coreeng.supportbot.slack.client;

import com.google.common.collect.ImmutableList;
import com.slack.api.model.block.LayoutBlock;

import org.jspecify.annotations.Nullable;

public record SimpleSlackView(
    ImmutableList<LayoutBlock> blocks,
    @Nullable
    String privateMetadata
) implements SlackView {
    @Override
    public ImmutableList<LayoutBlock> renderBlocks() {
        return blocks;
    }
}
