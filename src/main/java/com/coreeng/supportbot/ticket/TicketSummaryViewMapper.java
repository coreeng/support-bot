package com.coreeng.supportbot.ticket;

import com.coreeng.supportbot.EnumerationValue;
import com.coreeng.supportbot.json.JsonMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.block.composition.OptionObject;
import com.slack.api.model.block.element.RichTextElement;
import com.slack.api.model.block.element.RichTextSectionElement;
import com.slack.api.model.view.View;
import com.slack.api.model.view.ViewState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

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
    private final DateTimeFormatter dateFormatter;

    public View.ViewBuilder render(TicketSummaryView summaryView, View.ViewBuilder viewBuilder) {
        checkNotNull(summaryView);
        checkNotNull(viewBuilder);

        Metadata metadata = new Metadata(summaryView.ticketId().id());
        return viewBuilder
            .title(viewTitle(t -> t
                .type("plain_text")
                .text(format("Ticket ID-%d Summary", summaryView.ticketId().id()))))
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
                            // TODO: what timezone?
                            dateFormatter.format(summaryView.query().timestamp().atOffset(ZoneOffset.UTC)),
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
                    header(h -> h.text(plainText("Modify Ticket"))),
                    input(i -> i
                        .label(plainText("Change Status"))
                        .element(staticSelect(s -> s
                            .actionId(TicketField.status.actionId())
                            .initialOption(toOptionObject(summaryView.currentStatus()))
                            .options(List.of(
                                toOptionObject(TicketStatus.unresolved),
                                toOptionObject(TicketStatus.resolved)
                            ))
                        ))
                        .optional(false)),
                    input(i -> i
                        .label(plainText("Select Tags"))
                        .optional(true)
                        .hint(plainText("Select all applicable tags."))
                        .element(multiStaticSelect(s -> s
                            .actionId(TicketField.tags.actionId())
                            .initialOptions(
                                isEmpty(summaryView.currentTags())
                                    ? null
                                    : summaryView.currentTags().stream()
                                    .map(this::toOptionObject)
                                    .toList())
                            .options(summaryView.tags().stream()
                                .map(this::toOptionObject)
                                .toList())
                        ))),
                    input(i -> i
                        .label(plainText("Change Impact"))
                        .optional(true)
                        .element(staticSelect(s -> s
                            .actionId(TicketField.impact.actionId())
                            .placeholder(plainText("Not Evaluated"))
                            .initialOption(summaryView.currentImpact() != null
                                ? toOptionObject(summaryView.currentImpact())
                                : null)
                            .options(summaryView.impacts().stream()
                                .map(this::toOptionObject)
                                .toList())
                        )))
                ))
                .build()
            );
    }

    private List<RichTextElement> renderStatusHistory(ImmutableList<Ticket.StatusLog> statusLogs) {
        ImmutableList.Builder<RichTextElement> elements = ImmutableList.builder();
        StringBuilder str = new StringBuilder();
        for (int i = 0; i < statusLogs.size(); i++) {
            Ticket.StatusLog item = statusLogs.get(i);
            String circleEmoji = switch (item.status()) {
                case unresolved -> "large_orange_circle";
                case resolved -> "large_green_circle";
            };
            elements.add(RichTextSectionElement.Emoji.builder()
                .name(circleEmoji)
                .build());

            str.setLength(0);
            str.append(" ");
            // TODO: what timezone?
            str.append(item.status().renderMessage(dateFormatter.format(item.timestamp().atOffset(ZoneOffset.UTC))));
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

        ViewState.Value statusValue = checkNotNull(passedValues.get(TicketField.status.actionId()));
        ViewState.Value tagsValue = passedValues.get(TicketField.tags.actionId());
        ViewState.Value impactValue = passedValues.get(TicketField.impact.actionId());

        return new TicketSubmission(
            new TicketId(metadata.ticketId()),
            TicketStatus.valueOf(statusValue.getSelectedOption().getValue()),
            tagsValue != null && !isEmpty(tagsValue.getSelectedOptions())
                ? tagsValue.getSelectedOptions().stream()
                .map(ViewState.SelectedOption::getValue)
                .collect(toImmutableList())
                : ImmutableList.of(),
            impactValue != null && impactValue.getSelectedOption() != null
                ? impactValue.getSelectedOption().getValue()
                : null
        );
    }


    private OptionObject toOptionObject(TicketStatus status) {
        checkNotNull(status);
        return OptionObject.builder()
            .text(plainText(statusToLabel(status)))
            .value(status.toString())
            .build();
    }

    private OptionObject toOptionObject(EnumerationValue value) {
        checkNotNull(value);
        return OptionObject.builder()
            .text(plainText(value.name()))
            .value(value.code())
            .build();
    }

    private String statusToLabel(TicketStatus status) {
        return switch (status) {
            case unresolved -> "Unresolved";
            case resolved -> "Resolved";
        };
    }

    private record Metadata(
        long ticketId
    ) {
    }
}
