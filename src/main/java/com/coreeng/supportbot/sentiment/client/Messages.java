package com.coreeng.supportbot.sentiment.client;

import com.google.common.collect.ImmutableList;

public record Messages(
    ImmutableList<Message> messages
) {
}
