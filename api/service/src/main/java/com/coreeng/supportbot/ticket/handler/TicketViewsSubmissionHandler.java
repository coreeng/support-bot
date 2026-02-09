package com.coreeng.supportbot.ticket.handler;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.coreeng.supportbot.rbac.RbacService;
import com.coreeng.supportbot.slack.SlackId;
import com.coreeng.supportbot.slack.SlackViewSubmitHandler;
import com.coreeng.supportbot.ticket.EscalateViewMapper;
import com.coreeng.supportbot.ticket.TicketConfirmSubmissionMapper;
import com.coreeng.supportbot.ticket.TicketProcessingService;
import com.coreeng.supportbot.ticket.TicketSubmission;
import com.coreeng.supportbot.ticket.TicketSubmitResult;
import com.coreeng.supportbot.ticket.TicketSummaryViewMapper;
import com.coreeng.supportbot.ticket.TicketViewType;
import com.slack.api.app_backend.views.response.ViewSubmissionResponse;
import com.slack.api.bolt.context.builtin.ViewSubmissionContext;
import com.slack.api.bolt.request.builtin.ViewSubmissionRequest;
import static com.slack.api.model.view.Views.view;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
public class TicketViewsSubmissionHandler implements SlackViewSubmitHandler {
    private final TicketProcessingService ticketProcessingService;
    private final TicketSummaryViewMapper ticketSummaryViewMapper;
    private final EscalateViewMapper escalateViewMapper;
    private final TicketConfirmSubmissionMapper confirmSubmissionMapper;
    private final ExecutorService executor;
    private final RbacService rbacService;

    @Override
    public Pattern getPattern() {
        return TicketViewType.namePattern;
    }

    @Override
    @SuppressWarnings("FutureReturnValueIgnored")
    public ViewSubmissionResponse apply(ViewSubmissionRequest request, ViewSubmissionContext context) {
        if (!rbacService.isSupportBySlackId(SlackId.user(context.getRequestUserId()))) {
            log.atInfo()
                .addArgument(context.getRequestUserId())
                .log("Edit operation is not permitted for user({}), since it's not part of the support team");
            // Shouldn't happen, because we don't allow not-support users to open ticket forms,
            // So there is no actual need to display a proper message to user.
            // But it makes sense to leave this check for the safeguard.
            return new ViewSubmissionResponse();
        }
        
        String callbackId = request.getPayload().getView().getCallbackId();
        TicketViewType viewType = TicketViewType.fromCallbackIdOrNull(callbackId);
        switch (viewType) {
            case summary -> {
                TicketSubmission ticketSubmission = ticketSummaryViewMapper.extractSubmittedValues(
                    request.getPayload().getView()
                );
                TicketSubmitResult result = ticketProcessingService.submit(ticketSubmission);
                switch (result) {
                    case TicketSubmitResult.Success() -> {
                        return new ViewSubmissionResponse();
                    }
                    case TicketSubmitResult.RequiresConfirmation c -> {
                        return ViewSubmissionResponse.builder()
                            .view(view(v -> confirmSubmissionMapper.render(c, v)
                                .callbackId(TicketViewType.summaryConfirm.callbackId())
                                .type("modal")
                            ))
                            .responseAction("update")
                            .build();
                    }
                }
            }
            case summaryConfirm -> {
                var submission = confirmSubmissionMapper.parseTriggerInput(request.getPayload().getView().getPrivateMetadata());
                ticketProcessingService.submit(
                    submission.toBuilder()
                        .confirmed(true)
                        .build()
                );
                return new ViewSubmissionResponse();
            }
            case escalate -> {
                var escalateRequest = escalateViewMapper.extractSubmittedValues(request.getPayload().getView());
                Map<String, String> errors = escalateViewMapper.validate(escalateRequest);
                if (!errors.isEmpty()) {
                    return ViewSubmissionResponse.builder()
                        .responseAction("errors")
                        .errors(errors)
                        .build();
                }
                executor.submit(() -> ticketProcessingService.escalate(escalateRequest));
                return new ViewSubmissionResponse();
            }
            case null -> {
                log.warn("Unexpected callbackId({})", callbackId);
                return ViewSubmissionResponse.builder()
                    .errors(Map.of("Unexpected error occurred", ""))
                    .build();
            }
        }
    }
}
