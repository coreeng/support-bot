package com.coreeng.supportbot.escalation;

import com.coreeng.supportbot.EnumerationValue;
import com.coreeng.supportbot.config.EscalationProps;
import com.coreeng.supportbot.slack.MessageRef;
import com.google.common.collect.ImmutableList;
import com.slack.api.app_backend.interactive_components.payload.BlockActionPayload;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.block.composition.OptionObject;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.slack.api.model.block.Blocks.actions;
import static com.slack.api.model.block.Blocks.section;
import static com.slack.api.model.block.composition.BlockCompositions.plainText;
import static com.slack.api.model.block.element.BlockElements.staticSelect;

@Component
@RequiredArgsConstructor
public class EscalationFormMapper {
    private final EscalationProps escalationProps;

    public ImmutableList<LayoutBlock> renderForm() {
        return ImmutableList.of(
            section(s -> s
                .text(plainText("Ok, you've chosen to escalate a query. I'll need some further information to help you."))
            ),
            actions(a -> a
                .elements(List.of(
                    staticSelect(s -> s
                        .placeholder(plainText("Topic of query"))
                        .actionId(EscalationAction.changeTopic.actionId())
                        .options(
                            escalationProps.topics().stream().map(t -> OptionObject.builder()
                                .text(plainText(t.name()))
                                .value(t.code())
                                .build()
                            ).toList()
                        )
                    ),
                    staticSelect(s -> s
                        .placeholder(plainText("Escalate to"))
                        .actionId(EscalationAction.changeTeam.actionId())
                        .options(
                            escalationProps.teams().stream().map(t -> OptionObject.builder()
                                .text(plainText(t.name()))
                                .value(t.code())
                                .build()
                            ).toList()
                        )
                    )
                ))
            )
        );
    }

    public UpdateEscalationRequest mapToRequest(BlockActionPayload.Action action, MessageRef messageRef) {
        EscalationAction escalationAction = EscalationAction.valueOfOrNull(action.getActionId());
        if (escalationAction == null) {
            throw new IllegalArgumentException("Unknown escalation action: " + action.getActionId());
        }
        return switch (escalationAction) {
            case changeTopic -> new UpdateEscalationRequest(
                messageRef,
                findValue(action.getSelectedOption().getValue(), escalationProps.topics()),
                null
            );
            case changeTeam -> new UpdateEscalationRequest(
                messageRef,
                null,
                findValue(action.getSelectedOption().getValue(), escalationProps.teams())
            );
            default -> throw new IllegalArgumentException("Unknown escalation action: " + action.getActionId());
        };
    }

    private EnumerationValue findValue(String value, ImmutableList<EnumerationValue> values) {
        return values.stream()
            .filter(v -> v.code().equals(value))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown value: " + value));
    }
}
