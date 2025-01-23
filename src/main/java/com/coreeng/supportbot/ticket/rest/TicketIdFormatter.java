package com.coreeng.supportbot.ticket.rest;

import com.coreeng.supportbot.ticket.TicketId;
import org.springframework.format.Formatter;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class TicketIdFormatter implements Formatter<TicketId> {
    @Override
    public TicketId parse(String text, Locale locale) {
        try {
            return new TicketId(Long.parseLong(text));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid TicketId format", e);
        }
    }

    @Override
    public String print(TicketId object, Locale locale) {
        return Long.toString(object.id());
    }
}
