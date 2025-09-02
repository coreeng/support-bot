package com.coreeng.supportbot.ticket;

import com.coreeng.supportbot.slack.RenderingUtils;
import com.coreeng.supportbot.util.JsonMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.block.composition.OptionGroupObject;
import com.slack.api.model.block.composition.OptionObject;
import com.slack.api.model.block.element.RichTextElement;
import com.slack.api.model.block.element.RichTextSectionElement;
import com.slack.api.model.block.element.StaticSelectElement;
import com.slack.api.model.view.View;
import com.slack.api.model.view.ViewState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.coreeng.supportbot.slack.RenderingUtils.toOptionObject;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.Iterables.isEmpty;
import static com.slack.api.model.block.Blocks.*;
import static com.slack.api.model.block.composition.BlockCompositions.markdownText;
import static com.slack.api.model.block.composition.BlockCompositions.plainText;
import static com.slack.api.model.block.element.BlockElements.*;
import static com.slack.api.model.view.Views.*;
import static java.lang.String.format;

@Component
@RequiredArgsConstructor
public class TicketSummaryViewMapper {
    private final JsonMapper jsonMapper;

    public String createTriggerInput(TicketSummaryViewInput input) {
        checkNotNull(input);
        return jsonMapper.toJsonString(input);
    }

    public TicketSummaryViewInput parseTriggerInput(String input) {
        checkNotNull(input);
        return jsonMapper.fromJsonString(input, TicketSummaryViewInput.class);
    }

    public View.ViewBuilder render(TicketSummaryView summaryView, View.ViewBuilder viewBuilder) {
        checkNotNull(summaryView);
        checkNotNull(viewBuilder);

        Metadata metadata = new Metadata(summaryView.ticketId().id());
        return viewBuilder
            .title(viewTitle(t -> t
                .type("plain_text")
                .text(format("Ticket %s Summary", summaryView.ticketId().render()))))
            .submit(viewSubmit(s -> s
                .type("plain_text")
                .text("Apply Changes")
            ))
            .close(viewClose(c -> c
                .type("plain_text")
                .text("Close")
            ))
            .privateMetadata(jsonMapper.toJsonString(metadata))
            .blocks(ImmutableList.<LayoutBlock>builder()
                .add(header(h -> h.text(plainText("Ticket Summary"))))
                .addAll(summaryView.query().blocks())
                .addAll(asBlocks(
                    context(List.of(
                        markdownText(t -> t
                            .verbatim(false)
                            .text(format("""
                                    Sent by <@%s> | %s | <%s|View Message>
                                    """,
                                summaryView.query().senderId(),
                                formatSlackDate(summaryView.query().messageTs().getDate()),
                                summaryView.query().permalink()
                            )))
                    )),
                    divider(),
                    header(h -> h.text(plainText("Status History"))),
                    richText(r -> r
                        .elements(List.of(richTextSection(s -> s
                            .elements(renderStatusHistory(summaryView.statusLogs()))
                        )))
                    ),
                    divider(),
                    header(h -> h.text(plainText("Escalations")))
                ))
                .addAll(
                    isEmpty(summaryView.escalations())
                        ? renderNoEscalations()
                        : summaryView.escalations().stream()
                        .flatMap(e -> renderEscalation(e).stream())
                        .toList()
                )
                .addAll(asBlocks(
                    header(h -> h.text(plainText("Modify Ticket"))),
                    input(i -> i
                        .label(plainText("Change Status"))
                        .element(statusPicker(summaryView))
                        .optional(false)),
                    input(i -> i
                        .label(plainText("Select the Author's Team"))
                        .optional(false)
                        .element(renderTeamsInput(summaryView.teamsInput()))
                    ),
                    input(i -> i
                        .label(plainText("Select Tags"))
                        .optional(false)
                        .hint(plainText("Select all applicable tags."))
                        .element(multiStaticSelect(s -> s
                            .actionId(TicketField.tags.actionId())
                            .initialOptions(
                                isEmpty(summaryView.currentTags())
                                    ? null
                                    : summaryView.currentTags().stream()
                                    .map(RenderingUtils::toOptionObject)
                                    .toList())
                            .options(summaryView.tags().stream()
                                .map(RenderingUtils::toOptionObject)
                                .toList())
                        ))),
                    input(i -> i
                        .label(plainText("Change Impact"))
                        .optional(false)
                        .element(staticSelect(s -> s
                            .actionId(TicketField.impact.actionId())
                            .placeholder(plainText("Not Evaluated"))
                            .initialOption(summaryView.currentImpact() != null
                                ? toOptionObject(summaryView.currentImpact())
                                : null)
                            .options(summaryView.impacts().stream()
                                .map(RenderingUtils::toOptionObject)
                                .toList())
                        )))
                ))
                .build()
            );
    }

    private StaticSelectElement statusPicker(TicketSummaryView summaryView) {
        List<OptionObject> statusOptions = summaryView.currentStatus() == TicketStatus.stale
            ? Arrays.stream(TicketStatus.values())
            .map(RenderingUtils::toOptionObject)
            .toList()
            : Arrays.stream(TicketStatus.values())
            .filter(status -> !status.equals(TicketStatus.stale))
            .map(RenderingUtils::toOptionObject)
            .toList();
        return staticSelect(s -> s
            .actionId(TicketField.status.actionId())
            .initialOption(toOptionObject(summaryView.currentStatus()))
            .options(statusOptions)
        );
    }

    private ImmutableList<RichTextElement> renderStatusHistory(ImmutableList<Ticket.StatusLog> statusLogs) {
        ImmutableList.Builder<RichTextElement> elements = ImmutableList.builder();
        StringBuilder str = new StringBuilder();
        for (int i = 0; i < statusLogs.size(); i++) {
            Ticket.StatusLog item = statusLogs.get(i);
            elements.add(RichTextSectionElement.Emoji.builder()
                .name(item.status().emoji())
                .build());

            str.setLength(0);
            str.append(" ");
            str.append(item.status().label())
                .append(": ")
                .append(formatSlackDate(item.date()));
            str.append("\n");
            if (i < statusLogs.size() - 1) {
                // padding as spaces is applied so that bar is nicely aligned with the circle emoji
                str.append("  |\n");
            }
            elements.add(RichTextSectionElement.Text.builder()
                .text(str.toString())
                .build());
        }
        return elements.build();
    }

    private ImmutableList<LayoutBlock> renderEscalation(TicketSummaryView.EscalationView escalation) {
        return ImmutableList.of(
            section(s -> s
                .fields(ImmutableList.of(
                    plainText(t -> t
                        .text("Status: " + escalation.status().label())
                    ),
                    markdownText(t -> t
                        .text("Team: <!subteam^" + escalation.teamSlackGroupId() + ">")
                    )
                ))
            ),
            divider()
        );
    }

    private ImmutableList<LayoutBlock> renderNoEscalations() {
        return ImmutableList.of(
            context(c -> c
                .elements(ImmutableList.of(
                    plainText("No escalations for this ticket")
                ))
            ),
            divider()
        );
    }

    private StaticSelectElement renderTeamsInput(TicketSummaryView.TeamsInput teams) {
        ImmutableList.Builder<OptionGroupObject> optionGroupsBuilder = ImmutableList.builderWithExpectedSize(2);
        if (!teams.authorTeams().isEmpty()) {
            optionGroupsBuilder.add(
                OptionGroupObject.builder()
                    .label(plainText("Suggested teams"))
                    .options(
                        teams.authorTeams().stream()
                            .map(t -> OptionObject.builder()
                                .text(plainText(t))
                                .value(t)
                                .build())
                            .collect(toImmutableList())
                    )
                    .build()

            );
        }
        if (!teams.otherTeams().isEmpty()) {
            optionGroupsBuilder.add(
                OptionGroupObject.builder()
                    .label(plainText("Others"))
                    .options(
                        teams.otherTeams().stream()
                            .map(t -> OptionObject.builder()
                                .text(plainText(t))
                                .value(t)
                                .build())
                            .collect(toImmutableList())
                    )
                    .build()
            );
        }
        ImmutableList<OptionGroupObject> optionGroups = optionGroupsBuilder.build();
        return staticSelect(s -> {
                s.actionId(TicketField.team.actionId())
                    .initialOption(
                        teams.currentTeam() != null
                            ? OptionObject.builder()
                            .text(plainText(teams.currentTeam()))
                            .value(teams.currentTeam())
                            .build()
                            : null
                    );
                if (!optionGroups.isEmpty()) {
                    s.optionGroups(optionGroups);
                }
                return s;
            }
        );
    }

    public TicketSubmission extractSubmittedValues(View view) {
        checkNotNull(view);

        Metadata metadata = jsonMapper.fromJsonString(view.getPrivateMetadata(), Metadata.class);
        ImmutableMap<String, ViewState.Value> passedValues = view.getState().getValues().entrySet()
            .stream()
            .flatMap(entry -> entry.getValue().entrySet().stream())
            .collect(toImmutableMap(
                Map.Entry::getKey,
                Map.Entry::getValue
            ));

        ViewState.Value teamValue = checkNotNull(passedValues.get(TicketField.team.actionId()));
        ViewState.Value statusValue = checkNotNull(passedValues.get(TicketField.status.actionId()));
        ViewState.Value tagsValue = passedValues.get(TicketField.tags.actionId());
        ViewState.Value impactValue = passedValues.get(TicketField.impact.actionId());

        return TicketSubmission.builder()
            .ticketId(new TicketId(metadata.ticketId()))
            .status(TicketStatus.valueOf(statusValue.getSelectedOption().getValue()))
            .authorsTeam(teamValue.getSelectedOption().getValue())
            .tags(
                tagsValue != null && !isEmpty(tagsValue.getSelectedOptions())
                    ? tagsValue.getSelectedOptions().stream()
                    .map(ViewState.SelectedOption::getValue)
                    .collect(toImmutableList())
                    : ImmutableList.of()
            )
            .impact(
                impactValue != null && impactValue.getSelectedOption() != null
                    ? impactValue.getSelectedOption().getValue()
                    : null
            )
            .confirmed(false)
            .build();
    }

    private String formatSlackDate(Instant instant) {
        return "<!date^" + instant.getEpochSecond() + "^{date_short_pretty} at {time}|" + instant.truncatedTo(ChronoUnit.MINUTES) + ">";
    }

    private record Metadata(
        long ticketId
    ) {
    }
}
