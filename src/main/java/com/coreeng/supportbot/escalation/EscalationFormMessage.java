package com.coreeng.supportbot.escalation;

import com.coreeng.supportbot.slack.client.SlackMessage;
import com.google.common.collect.ImmutableList;
import com.slack.api.model.Attachment;

public record EscalationFormMessage(
   EscalationFormMapper formMapper
) implements SlackMessage {
    @Override
    public ImmutableList<Attachment> renderAttachments() {
        return ImmutableList.of(Attachment.builder()
            .fallback("Fill out escalation information")
            .blocks(formMapper.renderForm())
            .build());
    }
}
