package com.coreeng.supportbot.analysis.rest;

import com.coreeng.supportbot.analysis.AnalysisPromptLoadException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = AnalysisController.class)
@Slf4j
public class AnalysisExceptionHandler {

    @ExceptionHandler(AnalysisPromptLoadException.class)
    public ProblemDetail handlePromptLoadFailure(AnalysisPromptLoadException failure) {
        log.atError().setCause(failure).log("Failed to load analysis prompt");
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Analysis prompt is unavailable.");
        problem.setTitle("Analysis prompt unavailable");
        problem.setProperty("code", "ANALYSIS_PROMPT_LOAD_FAILED");
        return problem;
    }
}
