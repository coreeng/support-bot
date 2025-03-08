package com.coreeng.supportbot.escalation.rest;

import com.coreeng.supportbot.escalation.EscalationId;
import org.springframework.format.Formatter;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class EscalationIdFormatter implements Formatter<EscalationId> {
    @Override
    public EscalationId parse(String text, Locale locale) {
        try {
            return new EscalationId(Long.parseLong(text));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid TicketId format", e);
        }
    }

    @Override
    public String print(EscalationId object, Locale locale) {
        return Long.toString(object.id());
    }
}
