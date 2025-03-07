package com.coreeng.supportbot.slack;

public class InvalidPermalink extends RuntimeException {
    public InvalidPermalink(String message) {
        super(message);
    }

    public InvalidPermalink(Throwable cause) {
        super(cause);
    }
}
