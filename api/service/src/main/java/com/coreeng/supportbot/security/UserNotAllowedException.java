package com.coreeng.supportbot.security;

public class UserNotAllowedException extends RuntimeException {
    public UserNotAllowedException() {
        super("User not in allow list");
    }
}
