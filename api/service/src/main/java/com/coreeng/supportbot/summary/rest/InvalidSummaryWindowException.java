package com.coreeng.supportbot.summary.rest;

/**
 * The caller asked for a summary window the endpoint will not serve: one that ends before it
 * starts, or one wider than the maximum. {@link SummaryExceptionHandler} reports it as a 400.
 */
public class InvalidSummaryWindowException extends RuntimeException {
    public InvalidSummaryWindowException(String message) {
        super(message);
    }
}
