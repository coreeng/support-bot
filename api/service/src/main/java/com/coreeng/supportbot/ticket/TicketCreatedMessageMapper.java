package com.coreeng.supportbot.ticket;

import com.coreeng.supportbot.slack.client.SimpleSlackMessage;
import com.coreeng.supportbot.slack.client.SlackMessage;
import com.google.common.collect.ImmutableList;
import com.slack.api.model.Attachment;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.block.element.BlockElement;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.List;

import static com.slack.api.model.block.Blocks.*;
import static com.slack.api.model.block.composition.BlockCompositions.markdownText;
import static com.slack.api.model.block.composition.BlockCompositions.plainText;
import static com.slack.api.model.block.element.BlockElements.button;
import static java.lang.String.format;

@Component
@RequiredArgsConstructor
public class TicketCreatedMessageMapper {
    private final static String redHex = "#ff000d";
    private final static String purpleHex = "#b200ed";
    private final static String greenHex = "#00ff00";

    private final TicketSummaryViewMapper summaryViewMapper;
    private final EscalateViewMapper escalateViewMapper;

    public SlackMessage renderMessage(TicketCreatedMessage message) {
        return SimpleSlackMessage.builder()
            .text(getText(message))
            .blocks(renderBlocks(message))
            .attachments(renderAttachments(message))
            .build();
    }

    private String getText(TicketCreatedMessage message) {
        return "Ticket Created: " + message.ticketId().render();
    }

    private ImmutableList<LayoutBlock> renderBlocks(TicketCreatedMessage message) {
        return ImmutableList.of(
            section(s -> s.
                text(markdownText(format("*Ticket Created*: `%s`", message.ticketId().render())))
            )
        );
    }

    private ImmutableList<Attachment> renderAttachments(TicketCreatedMessage message) {
        String title = message.status().label() + ": " + formatSlackDate(message.statusChangedDate());
        ImmutableList.Builder<LayoutBlock> blocks = ImmutableList.builder();
        blocks.add(
            divider(),
            section(s -> s.text(markdownText(title)))
        );
        ImmutableList.Builder<BlockElement> secondaryButtons = ImmutableList.builder();
        secondaryButtons.add(
            button(b -> b
                .actionId(TicketOperation.summaryView.actionId())
                .value(summaryViewMapper.createTriggerInput(new TicketSummaryViewInput(message.ticketId())))
                .text(plainText("Full Summary")))
        );
        if (message.status() != TicketStatus.closed) {
            secondaryButtons.add(
                button(b -> b
                    .actionId(TicketOperation.escalate.actionId())
                    .value(escalateViewMapper.createTriggerInput(new TicketEscalateInput(message.ticketId())))
                    .text(plainText("Escalate"))
                )
            );
        }
        blocks.add(
            actions(secondaryButtons.build()),
            context(List.of(
                plainText(t -> t
                    .text(":pushpin: Options above supplied for Support engineers. Please ignore...")
                    .emoji(true))
            ))
        );
        return ImmutableList.of(Attachment.builder()
            .fallback(title)
            .blocks(blocks.build())
            .color(switch (message.status()) {
                case opened -> greenHex;
                case stale -> purpleHex;
                case closed -> redHex;
            })
            .build());
    }

    private String formatSlackDate(Instant instant) {
        return "<!date^" + instant.getEpochSecond() + "^{date_short_pretty} at {time}|" + instant.truncatedTo(ChronoUnit.MINUTES) + ">";
    }
}
