package com.coreeng.supportbot.troubleshooting.router;

/** The router produced nothing that can be acted on. The message is shown to engineers. */
public class RouterFailureException extends RuntimeException {
    public RouterFailureException(String message) {
        super(message);
    }

    public RouterFailureException(String message, Throwable cause) {
        super(message, cause);
    }
}
