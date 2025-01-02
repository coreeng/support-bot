package com.coreeng.supportbot.ticket.handler;

import com.coreeng.supportbot.slack.SlackBlockActionHandler;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.coreeng.supportbot.ticket.*;
import com.slack.api.app_backend.interactive_components.payload.BlockActionPayload;
import com.slack.api.bolt.context.builtin.ActionContext;
import com.slack.api.bolt.request.builtin.BlockActionRequest;
import com.slack.api.methods.request.views.ViewsOpenRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

import static com.slack.api.model.view.Views.view;

@Component
@Slf4j
@RequiredArgsConstructor
public class TicketActionsHandler implements SlackBlockActionHandler {
    private final TicketService ticketService;
    private final TicketSummaryViewMapper ticketSummaryViewMapper;
    private final SlackClient slackClient;

    @Override
    public Pattern getPattern() {
        return TicketOperation.namePattern;
    }

    @Override
    public void apply(BlockActionRequest req, ActionContext context) {
        for (BlockActionPayload.Action action : req.getPayload().getActions()) {
            TicketOperation operation = TicketOperation.fromStringOrNull(action.getActionId());
            switch (operation) {
                case TicketOperation.toggle -> ticketService.toggleStatus(ToggleTicketAction.fromRaw(req));
                case TicketOperation.summaryView -> {
                    TicketSummaryView summary = ticketService.summaryView(TicketSummaryViewQuery.fromRaw(req));
                    slackClient.viewsOpen(
                        ViewsOpenRequest.builder()
                            .triggerId(context.getTriggerId())
                            .view(view(v -> ticketSummaryViewMapper.render(summary, v)
                                .callbackId(TicketOperation.summarySubmit.publicName())
                                .type("modal")
                                .clearOnClose(true)
                            ))
                            .build()
                    );
                }
                case null, default -> log.warn("Unknown action: {}", action.getActionId());
            }
        }
    }
}
