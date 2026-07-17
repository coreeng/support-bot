package com.coreeng.supportbot.elevate;

public final class ElevateApiException extends RuntimeException {
    public ElevateApiException(String message) {
        super(message);
    }

    public ElevateApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
