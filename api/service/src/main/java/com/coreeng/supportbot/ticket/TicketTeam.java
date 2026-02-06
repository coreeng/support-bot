package com.coreeng.supportbot.ticket;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.jspecify.annotations.Nullable;

public sealed interface TicketTeam {
    String notATenantCode = "Not a Tenant";

    @JsonValue
    String toCode();

    @JsonCreator
    @Nullable
    static TicketTeam fromCode(@Nullable String code) {
        if (code == null) {
            return null;
        }
        if (notATenantCode.equals(code)) {
            return new UnknownTeam();
        }
        return new KnownTeam(code);
    }

    record KnownTeam(String code) implements TicketTeam {
        @Override
        public String toCode() { return code; }
    }

    record UnknownTeam() implements TicketTeam {
        @Override
        public String toCode() { return notATenantCode; }
    }
}
