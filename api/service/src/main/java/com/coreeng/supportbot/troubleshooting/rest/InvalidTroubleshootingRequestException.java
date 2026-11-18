package com.coreeng.supportbot.troubleshooting.rest;

class InvalidTroubleshootingRequestException extends RuntimeException {
    InvalidTroubleshootingRequestException(String message) {
        super(message);
    }
}
