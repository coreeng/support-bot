package com.coreeng.supportbot.docanalysis.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = DocAnalysisController.class)
public class DocAnalysisExceptionHandler {

    @ExceptionHandler(InvalidDocAnalysisWindowException.class)
    public ProblemDetail handleInvalidWindow(InvalidDocAnalysisWindowException failure) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, failure.getMessage());
        problem.setTitle("Invalid documentation analysis window");
        problem.setProperty("code", "DOCUMENTATION_ANALYSIS_WINDOW_INVALID");
        return problem;
    }
}
