package com.coreeng.supportbot.ticket;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public sealed interface TicketTeam {
    String notATenantCode = "Not a Tenant";

    @JsonValue
    String toCode();

    @JsonCreator
    static TicketTeam fromCode(String code) {
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
