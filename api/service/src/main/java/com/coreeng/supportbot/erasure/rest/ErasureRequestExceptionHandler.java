package com.coreeng.supportbot.erasure.rest;

import com.coreeng.supportbot.erasure.ErasureRequestNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Fixed messages only: the rejected input may hold the emails, which must never be echoed back. */
@RestControllerAdvice(assignableTypes = ErasureRequestController.class)
public class ErasureRequestExceptionHandler {

    @ExceptionHandler(ErasureRequestNotFoundException.class)
    public ProblemDetail notFound(ErasureRequestNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Erasure request not found", "ERASURE_REQUEST_NOT_FOUND");
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ProblemDetail invalidRequest(Exception exception) {
        return problem(HttpStatus.BAD_REQUEST, "Erasure request is invalid", "ERASURE_REQUEST_INVALID");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail invalidRequestId(MethodArgumentTypeMismatchException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Erasure request id is invalid", "ERASURE_REQUEST_ID_INVALID");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String code) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setTitle(title);
        problem.setProperty("code", code);
        return problem;
    }
}
