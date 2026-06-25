package com.coreeng.supportbot.prtracking.rest;

import com.coreeng.supportbot.prtracking.RequestBreakdownInvariantException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates {@link RequestBreakdownInvariantException}s into a clean {@code 500} response instead
 * of a raw stack trace. The invariant only breaks if the funnel aggregate returns incoherent counts
 * (a query or data regression), so it is a server fault: logged loudly here, while the dashboard
 * degrades to a dash on the affected funnel cards rather than failing the whole page.
 */
@RestControllerAdvice
@Slf4j
public class RequestBreakdownExceptionHandler {

    @ExceptionHandler(RequestBreakdownInvariantException.class)
    public ProblemDetail handleInvariantViolation(RequestBreakdownInvariantException ex) {
        log.atError().setCause(ex).log("Request breakdown aggregate returned incoherent counts — failing the request");
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "Request breakdown is temporarily unavailable.");
        problem.setTitle("Request breakdown unavailable");
        return problem;
    }
}
