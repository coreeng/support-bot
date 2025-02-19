package com.coreeng.supportbot.escalation;

import com.coreeng.supportbot.enums.Tag;
import com.coreeng.supportbot.slack.client.SimpleSlackMessage;
import com.coreeng.supportbot.util.JsonMapper;
import com.coreeng.supportbot.util.RelativeDateFormatter;
import com.google.common.collect.ImmutableList;
import com.slack.api.model.Attachment;
import com.slack.api.model.block.LayoutBlock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static com.google.common.collect.Iterables.isEmpty;
import static com.slack.api.model.block.Blocks.*;
import static com.slack.api.model.block.composition.BlockCompositions.markdownText;
import static com.slack.api.model.block.composition.BlockCompositions.plainText;
import static com.slack.api.model.block.element.BlockElements.button;
import static java.lang.String.format;

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
            .attachments(ImmutableList.of(renderAttachment(message)))
            .build();
    }

    private String getTextMessage(EscalationCreatedMessage message) {
        return format("Escalation created: %s", message.escalationId().render());
    }

    private ImmutableList<LayoutBlock> renderBlocks(EscalationCreatedMessage message) {
        StringBuilder str = new StringBuilder();
        str.append("Escalation created from <")
            .append(message.ticketQueryPermalink())
            .append("|ticket>: `")
            .append(message.escalationId().render())
            .append("`\nEscalated to: <!subteam^")
            .append(message.slackTeamGroupId())
            .append(">");
        if (!isEmpty(message.tags())) {
            str.append("\nTags:\n");
            for (Tag tag : message.tags()) {
                str.append("• ")
                    .append(tag.label())
                    .append("\n");
            }
        }
        return ImmutableList.of(
            section(s -> s
                .text(markdownText(str.toString()))
            )
        );
    }

    private Attachment renderAttachment(EscalationCreatedMessage message) {
        String statusText = message.status().label() + ": " + dateFormatter.format(message.statusChangedDate());
        ImmutableList.Builder<LayoutBlock> blocks = ImmutableList.builder();
        blocks.add(
            divider(),
            section(s -> s
                .text(plainText(statusText))
            )
        );
        if (message.status() != EscalationStatus.resolved) {
            blocks.add(actions(ImmutableList.of(
                button(b -> b
                    .actionId(EscalationOperation.resolve.actionId())
                    .value(jsonMapper.toJsonString(new EscalationResolveInput(message.escalationId())))
                    .text(plainText("Resolve escalation"))
                ))
            ));
        }
        return Attachment.builder()
            .color(message.status() == EscalationStatus.opened ? greenHex : redHex)
            .fallback("Escalation created message")
            .blocks(blocks.build())
            .build();
    }

    public EscalationResolveInput parseTriggerInput(String json) {
        return jsonMapper.fromJsonString(json, EscalationResolveInput.class);
    }
}
