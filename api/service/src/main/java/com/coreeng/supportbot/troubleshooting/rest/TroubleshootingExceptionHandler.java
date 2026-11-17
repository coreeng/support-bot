package com.coreeng.supportbot.troubleshooting.rest;

import com.coreeng.supportbot.troubleshooting.ProposalNotPendingException;
import com.coreeng.supportbot.troubleshooting.TicketNotFoundForSessionException;
import com.coreeng.supportbot.troubleshooting.TroubleshootingSessionNotFoundException;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = TroubleshootingController.class)
public class TroubleshootingExceptionHandler {

    @ExceptionHandler(TroubleshootingSessionNotFoundException.class)
    public ProblemDetail sessionNotFound(TroubleshootingSessionNotFoundException failure) {
        return problem(HttpStatus.NOT_FOUND, failure.getMessage(), "TROUBLESHOOTING_SESSION_NOT_FOUND");
    }

    @ExceptionHandler(TicketNotFoundForSessionException.class)
    public ProblemDetail ticketNotFound(TicketNotFoundForSessionException failure) {
        return problem(HttpStatus.NOT_FOUND, failure.getMessage(), "TROUBLESHOOTING_TICKET_NOT_FOUND");
    }

    /** A second decision, or one on a proposal a newer turn replaced. The card updates from the stream. */
    @ExceptionHandler(ProposalNotPendingException.class)
    public ProblemDetail proposalNotPending(ProposalNotPendingException failure) {
        return problem(HttpStatus.CONFLICT, failure.getMessage(), "TROUBLESHOOTING_PROPOSAL_NOT_PENDING");
    }

    @ExceptionHandler(InvalidTroubleshootingRequestException.class)
    public ProblemDetail invalid(InvalidTroubleshootingRequestException failure) {
        return problem(HttpStatus.BAD_REQUEST, failure.getMessage(), "TROUBLESHOOTING_REQUEST_INVALID");
    }

    private static ProblemDetail problem(HttpStatus status, @Nullable String detail, String code) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, Objects.requireNonNullElse(detail, code));
        problem.setProperty("code", code);
        return problem;
    }
}
