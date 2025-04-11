package com.coreeng.supportbot.ticket.handler;

import com.coreeng.supportbot.rbac.RbacService;
import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.slack.SlackBlockActionHandler;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.coreeng.supportbot.slack.client.SlackPostEphemeralMessageRequest;
import com.coreeng.supportbot.ticket.EscalateViewMapper;
import com.coreeng.supportbot.rbac.RbacRestrictionMessage;
import com.coreeng.supportbot.ticket.TicketEscalateInput;
import com.coreeng.supportbot.ticket.TicketOperation;
import com.coreeng.supportbot.ticket.TicketSummaryService;
import com.coreeng.supportbot.ticket.TicketSummaryView;
import com.coreeng.supportbot.ticket.TicketSummaryViewInput;
import com.coreeng.supportbot.ticket.TicketSummaryViewMapper;
import com.coreeng.supportbot.ticket.TicketViewType;
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
    private final TicketSummaryViewMapper summaryViewMapper;
    private final EscalateViewMapper escalateViewMapper;
    private final SlackClient slackClient;
    private final TicketSummaryService ticketSummaryService;
    private final RbacService rbacService;

    @Override
    public Pattern getPattern() {
        return TicketOperation.namePattern;
    }

    @Override
    public void apply(BlockActionRequest req, ActionContext context) {
        BlockActionPayload payload = req.getPayload();
        if (!rbacService.isSupportBySlackId(context.getRequestUserId())) {
            log.atInfo()
                .addArgument(context::getRequestUserId)
                .log("Rejecting escalation request. User({}) is not a support team member");
            slackClient.postEphemeralMessage(SlackPostEphemeralMessageRequest.builder()
                .message(new RbacRestrictionMessage())
                .channel(payload.getChannel().getId())
                .threadTs(MessageTs.ofOrNull(payload.getMessage().getThreadTs()))
                .userId(context.getRequestUserId())
                .build()
            );
            return;
        }

        for (BlockActionPayload.Action action : payload.getActions()) {
            TicketOperation operation = TicketOperation.fromActionIdOrNull(action.getActionId());
            switch (operation) {
                case summaryView -> openSummaryView(context, action);
                case escalate -> escalateTicket(context, action);
                case null -> log.warn("Unknown action: {}", action.getActionId());
            }
        }
    }

    private void openSummaryView(ActionContext context, BlockActionPayload.Action action) {
        TicketSummaryViewInput input = summaryViewMapper.parseTriggerInput(action.getValue());
        TicketSummaryView summary = ticketSummaryService.summaryView(input.ticketId());
        slackClient.viewsOpen(
            ViewsOpenRequest.builder()
                .triggerId(context.getTriggerId())
                .view(view(v -> summaryViewMapper.render(summary, v)
                    .callbackId(TicketViewType.summary.callbackId())
                    .type("modal")
                    .clearOnClose(true)
                ))
                .build()
        );
    }

    private void escalateTicket(ActionContext context, BlockActionPayload.Action action) {
        TicketEscalateInput input = escalateViewMapper.parseTriggerInput(action.getValue());
        slackClient.viewsOpen(
            ViewsOpenRequest.builder()
                .triggerId(context.getTriggerId())
                .view(view(v -> escalateViewMapper.render(input, v)
                    .callbackId(TicketViewType.escalate.callbackId())
                    .type("modal")
                    .clearOnClose(true)
                ))
                .build()
        );
    }
}
