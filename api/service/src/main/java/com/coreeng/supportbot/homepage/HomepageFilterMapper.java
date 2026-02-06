package com.coreeng.supportbot.homepage;

import com.coreeng.supportbot.config.TicketAssignmentProps;
import com.coreeng.supportbot.enums.EscalationTeamsRegistry;
import com.coreeng.supportbot.enums.ImpactsRegistry;
import com.coreeng.supportbot.enums.TagsRegistry;
import com.coreeng.supportbot.slack.RenderingUtils;
import com.coreeng.supportbot.teams.SupportTeamService;
import com.coreeng.supportbot.ticket.TicketStatus;
import com.coreeng.supportbot.ticket.TicketsQuery;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.block.composition.OptionObject;
import com.slack.api.model.view.View;
import com.slack.api.model.view.ViewState;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.coreeng.supportbot.slack.RenderingUtils.toOptionObject;
import static com.slack.api.model.block.composition.BlockCompositions.option;
import static com.coreeng.supportbot.slack.RenderingUtils.toOptionObjectOrNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.Iterables.isEmpty;
import static com.slack.api.model.block.Blocks.input;
import static com.slack.api.model.block.composition.BlockCompositions.plainText;
import static com.slack.api.model.block.element.BlockElements.multiStaticSelect;
import static com.slack.api.model.block.element.BlockElements.staticSelect;
import static com.slack.api.model.view.Views.*;

@Component
@RequiredArgsConstructor
public class HomepageFilterMapper {
    public static final String noTagsValue = "__no_tags__";
    public static final String noTagsLabel = "-- No Tags --";

    private final TagsRegistry tagsRegistry;
    private final ImpactsRegistry impactsRegistry;
    private final EscalationTeamsRegistry escalationTeamsRegistry;
    private final SupportTeamService supportTeamService;
    private final TicketAssignmentProps assignmentProps;

    public View.ViewBuilder render(HomepageView.State state, View.ViewBuilder view) {
        HomepageFilter filter = state.filter();

        List<LayoutBlock> filterBlocks = new ArrayList<>(List.of(
                input(i -> i
                    .label(plainText("Status"))
                    .optional(true)
                    .element(staticSelect(s -> s
                        .actionId(FilterField.status.actionId())
                        .initialOption(toOptionObjectOrNull(filter.status()))
                        .options(List.of(
                            toOptionObject(TicketStatus.opened),
                            toOptionObject(TicketStatus.closed)
                        ))
                    ))
                ),
                input(i -> i
                    .label(plainText("Escalation Team"))
                    .optional(true)
                    .element(staticSelect(s -> s
                        .actionId(FilterField.escalationTeam.actionId())
                        .initialOption(
                            filter.escalationTeam() == null
                                ? null
                                : toOptionObjectOrNull(escalationTeamsRegistry.findEscalationTeamByCode(filter.escalationTeam()))
                        )
                        .options(
                            escalationTeamsRegistry.listAllEscalationTeams().stream()
                                .map(RenderingUtils::toOptionObject)
                                .toList()
                        )
                    ))
                ),
                input(i -> i
                    .label(plainText("Order"))
                    .optional(true)
                    .element(staticSelect(s -> s
                        .actionId(FilterField.order.actionId())
                        .initialOption(toOptionObjectOrNull(filter.order()))
                        .options(List.of(
                            toOptionObject(TicketsQuery.Order.desc),
                            toOptionObject(TicketsQuery.Order.asc)
                        ))
                    ))
                ),
                input(i -> i
                    .label(plainText("Timeframe"))
                    .optional(true)
                    .element(staticSelect(s -> s
                        .actionId(FilterField.timeframe.actionId())
                        .initialOption(toOptionObjectOrNull(filter.timeframe()))
                        .options(List.of(
                            toOptionObject(HomepageFilter.Timeframe.thisWeek),
                            toOptionObject(HomepageFilter.Timeframe.previousWeek)
                        ))
                    ))
                ),
                input(i -> i
                    .label(plainText("Tags"))
                    .optional(true)
                    .element(multiStaticSelect(s -> s
                        .actionId(FilterField.tags.actionId())
                        .initialOptions(buildInitialTagOptions(filter))
                        .options(buildTagOptions())
                    ))
                ),
                input(i -> i
                    .label(plainText("Impact"))
                    .optional(true)
                    .element(staticSelect(s -> s
                        .actionId(FilterField.impact.actionId())
                        .initialOption(
                            filter.impact() == null
                                ? null
                                : toOptionObjectOrNull(impactsRegistry.findImpactByCode(filter.impact()))
                        )
                        .options(
                            impactsRegistry.listAllImpacts().stream()
                                .map(RenderingUtils::toOptionObject)
                                .toList()
                        )
                    ))
                )
        ));

        if (assignmentProps.enabled()) {
            ImmutableList<OptionObject> assigneeOptions = supportTeamService.members().stream()
                .map(member -> OptionObject.builder()
                    .text(plainText(member.email()))
                    .value(member.slackId().id())
                    .build())
                .collect(toImmutableList());

            filterBlocks.add(
                input(i -> i
                    .label(plainText("Assigned To"))
                    .optional(true)
                    .element(staticSelect(s -> s
                        .actionId(FilterField.assignedTo.actionId())
                        .initialOption(
                            filter.assignedTo() == null
                                ? null
                                : assigneeOptions.stream()
                                    .filter(opt -> opt.getValue().equals(filter.assignedTo()))
                                    .findFirst()
                                    .orElse(null)
                        )
                        .options(assigneeOptions)
                    ))
                )
            );
        }

        return view
            .title(viewTitle(t -> t
                .type("plain_text")
                .text("Tickets Filter")
            ))
            .submit(viewSubmit(s -> s
                .type("plain_text")
                .text("Apply Filters")
            ))
            .close(viewClose(c -> c
                .type("plain_text")
                .text("Cancel")
            ))
            .blocks(filterBlocks);
    }

    private List<OptionObject> buildTagOptions() {
        List<OptionObject> options = new ArrayList<>();
        options.add(option(plainText(noTagsLabel), noTagsValue));
        tagsRegistry.listAllTags().stream()
            .map(RenderingUtils::toOptionObject)
            .forEach(options::add);
        return options;
    }

    @Nullable
    private List<OptionObject> buildInitialTagOptions(HomepageFilter filter) {
        if (!filter.includeNoTags() && isEmpty(filter.tags())) {
            return null;
        }
        List<OptionObject> initialOptions = new ArrayList<>();
        if (filter.includeNoTags()) {
            initialOptions.add(option(plainText(noTagsLabel), noTagsValue));
        }
        tagsRegistry.listTagsByCodes(filter.tags()).stream()
            .map(RenderingUtils::toOptionObject)
            .forEach(initialOptions::add);
        return initialOptions;
    }

    public HomepageFilter extractSubmittedValues(View view) {
        HomepageFilter.HomepageFilterBuilder builder = HomepageFilter.builder();
        ImmutableMap<String, ViewState.Value> values = view.getState().getValues().entrySet()
                .stream()
                .flatMap(entry -> entry.getValue().entrySet().stream())
                .collect(toImmutableMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue
                ));

        ViewState.Value statusValue = values.get(FilterField.status.actionId());
        if (statusValue != null && statusValue.getSelectedOption() != null) {
            builder.status(TicketStatus.valueOf(statusValue.getSelectedOption().getValue()));
        }

        ViewState.Value escalationTeam = values.get(FilterField.escalationTeam.actionId());
        if (escalationTeam != null && escalationTeam.getSelectedOption() != null) {
            builder.escalationTeam(escalationTeam.getSelectedOption().getValue());
        }

        ViewState.Value orderValue = values.get(FilterField.order.actionId());
        if (orderValue != null && orderValue.getSelectedOption() != null) {
            builder.order(TicketsQuery.Order.valueOf(orderValue.getSelectedOption().getValue()));
        }

        ViewState.Value timeframeValue = values.get(FilterField.timeframe.actionId());
        if (timeframeValue != null && timeframeValue.getSelectedOption() != null) {
            builder.timeframe(HomepageFilter.Timeframe.valueOf(timeframeValue.getSelectedOption().getValue()));
        }

        ViewState.Value tagsValue = values.get(FilterField.tags.actionId());
        if (tagsValue != null && tagsValue.getSelectedOptions() != null) {
            List<String> selectedValues = tagsValue.getSelectedOptions().stream()
                .map(ViewState.SelectedOption::getValue)
                .toList();
            builder.includeNoTags(selectedValues.contains(noTagsValue));
            builder.tags(selectedValues.stream()
                .filter(value -> !noTagsValue.equals(value))
                .collect(toImmutableList()));
        }

        ViewState.Value impactValue = values.get(FilterField.impact.actionId());
        if (impactValue != null && impactValue.getSelectedOption() != null) {
            builder.impact(impactValue.getSelectedOption().getValue());
        }

        ViewState.Value assignedToValue = values.get(FilterField.assignedTo.actionId());
        if (assignedToValue != null && assignedToValue.getSelectedOption() != null) {
            builder.assignedTo(assignedToValue.getSelectedOption().getValue());
        }

        return builder.build();
    }

    @Getter
    @RequiredArgsConstructor
    public enum FilterField {
        status("homepage-filter-status"),
        order("homepage-filter-order"),
        timeframe("homepage-filter-timeframe"),
        tags("homepage-filter-tags"),
        impact("homepage-filter-impact"),
        escalationTeam("homepage-filter-escalation-team"),
        inquiringTeam("homepage-filter-inquiring-team"),
        assignedTo("homepage-filter-assigned-to");

        private final String actionId;
    }
}
