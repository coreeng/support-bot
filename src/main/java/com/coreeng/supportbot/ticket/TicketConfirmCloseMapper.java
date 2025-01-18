package com.coreeng.supportbot.ticket;

import com.coreeng.supportbot.util.JsonMapper;
import com.google.common.collect.ImmutableList;
import com.slack.api.model.view.View;
import org.springframework.stereotype.Component;

import static com.slack.api.model.block.Blocks.section;
import static com.slack.api.model.block.composition.BlockCompositions.markdownText;
import static com.slack.api.model.view.Views.*;

@Component
public class TicketConfirmCloseMapper {
    private final JsonMapper jsonMapper;

    public TicketConfirmCloseMapper(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public View.ViewBuilder render(ToggleResult.RequiresConfirmation model, View.ViewBuilder view) {
        return view
            .title(viewTitle(t -> t
                .type("plain_text")
                .text("Closing Ticket")
            ))
            .submit(viewSubmit(s -> s
                .type("plain_text")
                .text("Confirm")
            ))
            .close(viewClose(c -> c
                .type("plain_text")
                .text("Cancel")
            ))
            .privateMetadata(jsonMapper.toJsonString(new InputMetadata(model.ticketId())))
            .blocks(ImmutableList.of(
                section(s -> s
                    .text(markdownText(t -> t
                        .text(getMessage(model))
                    ))
                )
            ));
    }

    public InputMetadata parseTriggerInput(String privateMetadata) {
        return jsonMapper.fromJsonString(privateMetadata, InputMetadata.class);
    }

    private String getMessage(ToggleResult.RequiresConfirmation model) {
        return "Ticket has `" + model.unresolvedEscalations() + "` unresolved escalations. "
            + "Closing the ticket will close all related escalations.\n"
            + "Are you sure?";
    }

    public record InputMetadata(
        TicketId ticketId
    ) {
    }
}
