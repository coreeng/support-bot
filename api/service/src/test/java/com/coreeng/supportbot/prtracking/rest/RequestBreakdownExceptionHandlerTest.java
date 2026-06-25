package com.coreeng.supportbot.prtracking.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.coreeng.supportbot.prtracking.InterventionExceedsPrTicketsException;
import com.coreeng.supportbot.prtracking.NegativeCountException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

class RequestBreakdownExceptionHandlerTest {

    private final RequestBreakdownExceptionHandler handler = new RequestBreakdownExceptionHandler();

    @Test
    void mapsInvariantViolationToInternalServerError() {
        ProblemDetail problem = handler.handleInvariantViolation(new NegativeCountException(-1, 0, 0));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(problem.getTitle()).isEqualTo("Request breakdown unavailable");
    }

    @Test
    void handlesEverySubtypeViaTheSealedBase() {
        // The handler is declared against the base type, so any concrete subtype is accepted.
        ProblemDetail problem = handler.handleInvariantViolation(new InterventionExceedsPrTicketsException(4, 3));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
    }
}
