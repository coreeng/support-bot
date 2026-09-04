package com.coreeng.supportbot.summary.rest;

import com.coreeng.supportbot.analysis.AnalysisPromptLoadException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = SummaryController.class)
@Slf4j
public class SummaryExceptionHandler {

    /** A bad window is the caller's mistake, not a server fault — 400, with the reason. */
    @ExceptionHandler(InvalidSummaryWindowException.class)
    public ProblemDetail handleInvalidWindow(InvalidSummaryWindowException failure) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, failure.getMessage());
        problem.setTitle("Invalid summary window");
        problem.setProperty("code", "SUMMARY_WINDOW_INVALID");
        return problem;
    }

    /**
     * The classification prompt is needed to compute the prompt ID every figure is scoped to, so
     * without it there is nothing to show — unlike a summary failure, which degrades gracefully.
     */
    @ExceptionHandler(AnalysisPromptLoadException.class)
    public ProblemDetail handlePromptLoadFailure(AnalysisPromptLoadException failure) {
        log.atError().setCause(failure).log("Failed to load the analysis prompt for the summary page");
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Analysis prompt is unavailable.");
        problem.setTitle("Analysis prompt unavailable");
        problem.setProperty("code", "ANALYSIS_PROMPT_LOAD_FAILED");
        return problem;
    }
}
