package com.coreeng.supportbot.slack.client;

import com.google.common.collect.ImmutableList;
import com.slack.api.model.block.LayoutBlock;

import javax.annotation.Nullable;

public interface SlackView {
    ImmutableList<LayoutBlock> renderBlocks();
    @Nullable
    String privateMetadata();
}
