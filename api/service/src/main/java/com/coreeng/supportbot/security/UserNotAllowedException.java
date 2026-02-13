package com.coreeng.supportbot.security;

public class UserNotAllowedException extends RuntimeException {
    public UserNotAllowedException(String email) {
        super("User not in allow list: " + email);
    }
}
