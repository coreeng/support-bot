package com.coreeng.supportbot.elevate;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = ElevateStatusController.class)
public class ElevateExceptionHandler {
    @ExceptionHandler(ElevateSnapshotChangedException.class)
    public ProblemDetail handleSnapshotChanged(ElevateSnapshotChangedException failure) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, failure.getMessage());
        problem.setTitle("Elevate snapshot changed");
        problem.setProperty("code", "SNAPSHOT_CHANGED");
        return problem;
    }

    @ExceptionHandler(ElevateResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ElevateResourceNotFoundException failure) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, failure.getMessage());
        problem.setTitle("Elevate resource not found");
        problem.setProperty("code", "ELEVATE_RESOURCE_NOT_FOUND");
        problem.setProperty("resource", failure.resourceType());
        return problem;
    }
}
