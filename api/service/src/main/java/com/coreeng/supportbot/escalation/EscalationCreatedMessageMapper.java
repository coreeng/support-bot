package com.coreeng.supportbot.escalation;

import static com.slack.api.model.block.Blocks.*;
import static com.slack.api.model.block.composition.BlockCompositions.markdownText;
import static java.lang.String.format;

import com.coreeng.supportbot.slack.client.SimpleSlackMessage;
import com.coreeng.supportbot.util.JsonMapper;
import com.google.common.collect.ImmutableList;
import com.slack.api.model.block.LayoutBlock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EscalationCreatedMessageMapper {
    private final JsonMapper jsonMapper;

    public SimpleSlackMessage renderMessage(EscalationCreatedMessage message) {
        return SimpleSlackMessage.builder()
                .blocks(renderBlocks(message))
                .text(getTextMessage(message))
                .build();
    }

    private ImmutableList<LayoutBlock> renderBlocks(EscalationCreatedMessage message) {
        String str = "\nEscalated to team: " + message.team().label() + "(" + "<!subteam^"
                + message.team().slackGroupId() + ">)";
        return ImmutableList.of(section(s -> s.text(markdownText(str))));
    }

    private String getTextMessage(EscalationCreatedMessage message) {
        return format("Escalation to team: %s", message.team().label());
    }

    public EscalationResolveInput parseTriggerInput(String json) {
        return jsonMapper.fromJsonString(json, EscalationResolveInput.class);
    }
}
