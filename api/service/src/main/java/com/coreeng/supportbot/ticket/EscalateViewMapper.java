package com.coreeng.supportbot.ticket;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.slack.api.model.block.Blocks.input;
import static com.slack.api.model.block.composition.BlockCompositions.plainText;
import static com.slack.api.model.block.element.BlockElements.*;
import static com.slack.api.model.view.Views.*;

import com.coreeng.supportbot.enums.EscalationTeamsRegistry;
import com.coreeng.supportbot.slack.RenderingUtils;
import com.coreeng.supportbot.util.JsonMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.view.View;
import com.slack.api.model.view.ViewState;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class EscalateViewMapper {
    private final JsonMapper jsonMapper;
    private final EscalationTeamsRegistry escalationTeamsRegistry;

    public View.ViewBuilder render(TicketEscalateInput input, View.ViewBuilder view) {
        return view.title(viewTitle(t -> t.type("plain_text")
                        .text("Escalate Ticket " + input.ticketId().render())))
                .close(viewClose(c -> c.type("plain_text").text("Cancel")))
                .submit(viewSubmit(s -> s.type("plain_text").text("Escalate")))
                .privateMetadata(jsonMapper.toJsonString(new Input(input.ticketId())))
                .blocks(renderBlocks());
    }

    private List<LayoutBlock> renderBlocks() {
        return ImmutableList.of(
                input(i -> i.optional(false)
                        .label(plainText("Pick tags"))
                        .blockId(Fields.tags.actionId())
                        .element(multiExternalSelect(
                                s -> s.actionId(Fields.tags.actionId()).minQueryLength(0)))),
                input(i -> i.optional(false)
                        .label(plainText("Team to escalate to"))
                        .blockId(Fields.team.actionId())
                        .element(staticSelect(s -> s.actionId(Fields.team.actionId())
                                .options(escalationTeamsRegistry.listAllEscalationTeams().stream()
                                        .map(RenderingUtils::toOptionObject)
                                        .toList())))));
    }

    public String createTriggerInput(TicketEscalateInput input) {
        return jsonMapper.toJsonString(input);
    }

    public TicketEscalateInput parseTriggerInput(String json) {
        return jsonMapper.fromJsonString(json, TicketEscalateInput.class);
    }

    public EscalateRequest extractSubmittedValues(View view) {
        checkNotNull(view);

        Input input = jsonMapper.fromJsonString(view.getPrivateMetadata(), Input.class);
        ImmutableMap<String, ViewState.Value> passedValues = view.getState().getValues().entrySet().stream()
                .flatMap(entry -> entry.getValue().entrySet().stream())
                .collect(toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));

        ViewState.Value tagsValue =
                checkNotNull(passedValues.get(Fields.tags.actionId()), "tags field missing from escalation view state");
        if (tagsValue.getSelectedOptions() == null) {
            log.atWarn()
                    .addKeyValue("ticketId", input.ticketId())
                    .log("Escalation tags field had null selectedOptions — treating as empty");
        }
        ImmutableList<String> tags = tagsValue.getSelectedOptions() != null
                ? tagsValue.getSelectedOptions().stream()
                        .map(ViewState.SelectedOption::getValue)
                        .collect(toImmutableList())
                : ImmutableList.of();

        ViewState.Value teamValue =
                checkNotNull(passedValues.get(Fields.team.actionId()), "team field missing from escalation view state");
        String team = checkNotNull(teamValue.getSelectedOption(), "team selection is null in escalation view state")
                .getValue();

        return EscalateRequest.builder()
                .tags(tags)
                .ticketId(input.ticketId())
                .team(team)
                .build();
    }

    public String toActionId(TicketEscalationValidator.Field field) {
        return switch (field) {
            case team -> Fields.team.actionId();
            case tags -> Fields.tags.actionId();
        };
    }

    @Getter
    @RequiredArgsConstructor
    private enum Fields {
        tags("escalation-tags"),
        team("escalation-team");

        private final String actionId;
    }

    private record Input(TicketId ticketId) {}
}
