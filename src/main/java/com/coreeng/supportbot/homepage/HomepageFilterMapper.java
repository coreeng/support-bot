package com.coreeng.supportbot.homepage;

import com.coreeng.supportbot.config.TicketProps;
import com.coreeng.supportbot.slack.RenderingUtils;
import com.coreeng.supportbot.ticket.TicketStatus;
import com.coreeng.supportbot.ticket.TicketsQuery;
import com.google.common.collect.ImmutableMap;
import com.slack.api.model.view.View;
import com.slack.api.model.view.ViewState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static com.coreeng.supportbot.slack.RenderingUtils.toOptionObject;
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
    private final TicketProps ticketProps;

    public View.ViewBuilder render(HomepageView.State state, View.ViewBuilder view) {
        HomepageFilter filter = state.filter();
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
            .blocks(List.of(
                input(i -> i
                    .label(plainText("Status"))
                    .optional(true)
                    .element(staticSelect(s -> s
                        .actionId(FilterField.status.name())
                        .initialOption(toOptionObjectOrNull(filter.status()))
                        .options(List.of(
                            toOptionObject(TicketStatus.opened),
                            toOptionObject(TicketStatus.closed)
                        ))
                    ))
                ),
                input(i -> i
                    .label(plainText("Order"))
                    .optional(true)
                    .element(staticSelect(s -> s
                        .actionId(FilterField.order.name())
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
                        .actionId(FilterField.timeframe.name())
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
                        .actionId(FilterField.tags.name())
                        .initialOptions(
                            isEmpty(filter.tags())
                                ? null
                                : ticketProps.tags().stream()
                                .filter(t -> filter.tags().contains(t.code()))
                                .map(RenderingUtils::toOptionObject)
                                .toList()
                        )
                        .options(
                            ticketProps.tags().stream()
                                .map(RenderingUtils::toOptionObject)
                                .toList()
                        )
                    ))
                ),
                input(i -> i
                    .label(plainText("Impact"))
                    .optional(true)
                    .element(staticSelect(s -> s
                        .actionId(FilterField.impact.name())
                        .initialOption(
                            filter.impact() == null
                                ? null
                                : ticketProps.impacts().stream()
                                .filter(impact -> impact.code().equals(filter.impact()))
                                .map(RenderingUtils::toOptionObject)
                                .findFirst().get()
                        )
                        .options(
                            ticketProps.impacts().stream()
                                .map(RenderingUtils::toOptionObject)
                                .toList()
                        )
                    ))
                )
            ));
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

        ViewState.Value statusValue = values.get(FilterField.status.name());
        if (statusValue != null && statusValue.getSelectedOption() != null) {
            builder.status(TicketStatus.valueOf(statusValue.getSelectedOption().getValue()));
        }

        ViewState.Value orderValue = values.get(FilterField.order.name());
        if (orderValue != null && orderValue.getSelectedOption() != null) {
            builder.order(TicketsQuery.Order.valueOf(orderValue.getSelectedOption().getValue()));
        }

        ViewState.Value timeframeValue = values.get(FilterField.timeframe.name());
        if (timeframeValue != null && timeframeValue.getSelectedOption() != null) {
            builder.timeframe(HomepageFilter.Timeframe.valueOf(timeframeValue.getSelectedOption().getValue()));
        }

        ViewState.Value tagsValue = values.get(FilterField.tags.name());
        if (tagsValue != null && tagsValue.getSelectedOptions() != null) {
            builder.tags(tagsValue.getSelectedOptions().stream()
                .map(ViewState.SelectedOption::getValue)
                .collect(toImmutableList()));
        }

        ViewState.Value impactValue = values.get(FilterField.impact.name());
        if (impactValue != null && impactValue.getSelectedOption() != null) {
            builder.impact(impactValue.getSelectedOption().getValue());
        }

        return builder.build();
    }

    public enum FilterField {
        status,
        order,
        timeframe,
        tags,
        impact
    }
}
