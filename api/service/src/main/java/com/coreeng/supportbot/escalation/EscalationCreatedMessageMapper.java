package com.coreeng.supportbot.escalation;

import static com.slack.api.model.block.Blocks.*;
import static com.slack.api.model.block.composition.BlockCompositions.markdownText;
import static java.lang.String.format;

import com.coreeng.supportbot.slack.client.SimpleSlackMessage;
import com.coreeng.supportbot.util.JsonMapper;
import com.coreeng.supportbot.util.RelativeDateFormatter;
import com.google.common.collect.ImmutableList;
import com.slack.api.model.block.LayoutBlock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EscalationCreatedMessageMapper {
    private final static String redHex = "#ff000d";
    private final static String greenHex = "#00ff00";

    private final JsonMapper jsonMapper;
    private final RelativeDateFormatter dateFormatter;

    public SimpleSlackMessage renderMessage(EscalationCreatedMessage message) {
        return SimpleSlackMessage.builder()
            .text(getTextMessage(message))
            .blocks(renderBlocks(message))
            .build();
    }

    private String getTextMessage(EscalationCreatedMessage message) {
        return format("Escalation created: %s", message.escalationId().render());
    }

    private ImmutableList<LayoutBlock> renderBlocks(EscalationCreatedMessage message) {
        String str = "\nEscalated to: <!subteam^" +
                message.slackTeamGroupId() +
                ">";
        return ImmutableList.of(
            section(s -> s
                .text(markdownText(str))
            )
        );
    }

    public EscalationResolveInput parseTriggerInput(String json) {
        return jsonMapper.fromJsonString(json, EscalationResolveInput.class);
    }
}
