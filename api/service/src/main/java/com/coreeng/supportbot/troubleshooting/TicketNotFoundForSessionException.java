package com.coreeng.supportbot.troubleshooting;

public class TicketNotFoundForSessionException extends RuntimeException {
    public TicketNotFoundForSessionException(long ticketId) {
        super("Ticket " + ticketId + " does not exist");
    }
}
