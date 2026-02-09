package com.coreeng.supportbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.jspecify.annotations.Nullable;

@ConfigurationProperties(prefix = "ticket.assignment")
public record TicketAssignmentProps(
    boolean enabled,
    Encryption encryption
) {
    public TicketAssignmentProps {
        if (encryption == null) {
            encryption = new Encryption(false, null);
        }
    }

    public record Encryption(
        boolean enabled,
        @Nullable String key
    ) {
    }
}

