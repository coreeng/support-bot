package com.coreeng.supportbot.ticket;

import static com.slack.api.model.block.Blocks.section;
import static com.slack.api.model.block.composition.BlockCompositions.markdownText;
import static com.slack.api.model.view.Views.*;

import com.coreeng.supportbot.util.JsonMapper;
import com.google.common.collect.ImmutableList;
import com.slack.api.model.view.View;
import org.springframework.stereotype.Component;

@Component
public class TicketConfirmSubmissionMapper {
    private final JsonMapper jsonMapper;

    public TicketConfirmSubmissionMapper(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public View.ViewBuilder render(TicketSubmitResult.RequiresConfirmation model, View.ViewBuilder view) {
        return view.title(viewTitle(t -> t.type("plain_text").text("Closing Ticket")))
                .submit(viewSubmit(s -> s.type("plain_text").text("Confirm")))
                .close(viewClose(c -> c.type("plain_text").text("Cancel")))
                .privateMetadata(jsonMapper.toJsonString(model.submission()))
                .blocks(ImmutableList.of(section(s -> s.text(markdownText(t -> t.text(getMessage(model.cause())))))));
    }

    public TicketSubmission parseTriggerInput(String privateMetadata) {
        return jsonMapper.fromJsonString(privateMetadata, TicketSubmission.class);
    }

    private String getMessage(TicketSubmitResult.ConfirmationCause model) {
        return "Ticket has `" + model.unresolvedEscalations() + "` unresolved escalations. "
                + "Closing the ticket will close all related escalations.\n"
                + "Are you sure?";
    }
}
