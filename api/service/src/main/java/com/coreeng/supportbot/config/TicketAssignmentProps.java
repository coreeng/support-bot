package com.coreeng.supportbot.config;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ticket.assignment")
public record TicketAssignmentProps(boolean enabled, Encryption encryption) {
    public TicketAssignmentProps {
        if (encryption == null) {
            encryption = new Encryption(false, null);
        }
    }

    public record Encryption(boolean enabled, @Nullable String key) {}
}
