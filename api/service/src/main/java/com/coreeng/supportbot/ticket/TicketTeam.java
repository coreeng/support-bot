package com.coreeng.supportbot.ticket;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = TicketTeam.KnownTeam.class, name = "known"),
    @JsonSubTypes.Type(value = TicketTeam.UnknownTeam.class, name = "unknown")
})
public sealed interface TicketTeam {
    String NOT_A_TENANT_CODE = "Not a Tenant";

    String toCode();

    static TicketTeam fromCode(String code) {
        if (code == null) return null;
        if (NOT_A_TENANT_CODE.equals(code)) return new UnknownTeam();
        return new KnownTeam(code);
    }

    record KnownTeam(String code) implements TicketTeam {
        @Override
        public String toCode() { return code; }
    }

    record UnknownTeam() implements TicketTeam {
        @Override
        public String toCode() { return NOT_A_TENANT_CODE; }
    }
}
