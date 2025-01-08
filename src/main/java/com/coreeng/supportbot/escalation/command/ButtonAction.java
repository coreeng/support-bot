package com.coreeng.supportbot.escalation.command;

import com.coreeng.supportbot.slack.MessageRef;

public record ButtonAction(
    MessageRef messageRef,
    CommandButton button
) {
}
