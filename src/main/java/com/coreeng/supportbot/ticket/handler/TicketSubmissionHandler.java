package com.coreeng.supportbot.ticket.handler;

import com.coreeng.supportbot.slack.SlackViewSubmitHandler;
import com.coreeng.supportbot.ticket.TicketOperation;
import com.coreeng.supportbot.ticket.TicketService;
import com.coreeng.supportbot.ticket.TicketSubmission;
import com.coreeng.supportbot.ticket.TicketSummaryViewMapper;
import com.slack.api.app_backend.views.response.ViewSubmissionResponse;
import com.slack.api.bolt.context.builtin.ViewSubmissionContext;
import com.slack.api.bolt.request.builtin.ViewSubmissionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
@Slf4j
@RequiredArgsConstructor
public class TicketSubmissionHandler implements SlackViewSubmitHandler {
    private final TicketService ticketService;
    private final TicketSummaryViewMapper ticketSummaryViewMapper;

    @Override
    public Pattern getPattern() {
        return TicketOperation.namePattern;
    }

    @Override
    public ViewSubmissionResponse apply(ViewSubmissionRequest request, ViewSubmissionContext context) {
        TicketSubmission ticketSubmission = ticketSummaryViewMapper.extractSubmittedValues(
            request.getPayload().getView()
        );
        ticketService.submit(ticketSubmission);
        return new ViewSubmissionResponse();
    }
}
