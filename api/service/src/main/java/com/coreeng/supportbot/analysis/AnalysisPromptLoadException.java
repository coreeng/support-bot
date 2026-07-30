package com.coreeng.supportbot.analysis;

public class AnalysisPromptLoadException extends RuntimeException {
    public AnalysisPromptLoadException(String message) {
        super(message);
    }

    public AnalysisPromptLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
