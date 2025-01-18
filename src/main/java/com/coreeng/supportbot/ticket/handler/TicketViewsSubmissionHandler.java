package com.coreeng.supportbot.ticket.handler;

import com.coreeng.supportbot.slack.SlackViewSubmitHandler;
import com.coreeng.supportbot.ticket.EscalateViewMapper;
import com.coreeng.supportbot.ticket.TicketConfirmCloseMapper;
import com.coreeng.supportbot.ticket.TicketProcessingService;
import com.coreeng.supportbot.ticket.TicketSubmission;
import com.coreeng.supportbot.ticket.TicketSummaryViewMapper;
import com.coreeng.supportbot.ticket.TicketViewType;
import com.slack.api.app_backend.views.response.ViewSubmissionResponse;
import com.slack.api.bolt.context.builtin.ViewSubmissionContext;
import com.slack.api.bolt.request.builtin.ViewSubmissionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.regex.Pattern;

@Component
@Slf4j
@RequiredArgsConstructor
public class TicketViewsSubmissionHandler implements SlackViewSubmitHandler {
    private final TicketProcessingService ticketProcessingService;
    private final TicketSummaryViewMapper ticketSummaryViewMapper;
    private final EscalateViewMapper escalateViewMapper;
    private final TicketConfirmCloseMapper confirmCloseMapper;
    private final ExecutorService executor;

    @Override
    public Pattern getPattern() {
        return TicketViewType.namePattern;
    }

    @Override
    public ViewSubmissionResponse apply(ViewSubmissionRequest request, ViewSubmissionContext context) {
        String callbackId = request.getPayload().getView().getCallbackId();
        @Nullable TicketViewType viewType = TicketViewType.fromCallbackIdOrNull(callbackId);
        switch (viewType) {
            case summary -> {
                TicketSubmission ticketSubmission = ticketSummaryViewMapper.extractSubmittedValues(
                    request.getPayload().getView()
                );
                ticketProcessingService.submit(ticketSubmission);
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
            case confirmClose -> {
                var metadata = confirmCloseMapper.parseTriggerInput(request.getPayload().getView().getPrivateMetadata());
                ticketProcessingService.close(metadata.ticketId());
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
