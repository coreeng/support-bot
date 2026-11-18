package com.coreeng.supportbot.troubleshooting;

public class TroubleshootingSessionNotFoundException extends RuntimeException {
    public TroubleshootingSessionNotFoundException(long ticketId) {
        super("No troubleshooting session for ticket " + ticketId);
    }
}
