package com.coreeng.supportbot.docanalysis.rest;

/** A window that ends before it starts, or is wider than the maximum; reported as a 400. */
public class InvalidDocAnalysisWindowException extends RuntimeException {
    public InvalidDocAnalysisWindowException(String message) {
        super(message);
    }
}
