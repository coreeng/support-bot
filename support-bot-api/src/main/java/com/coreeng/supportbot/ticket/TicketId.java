package com.coreeng.supportbot.ticket;

import com.fasterxml.jackson.annotation.JsonValue;

public record TicketId(
    @JsonValue long id
) {
    public String render() {
        return "ID-" + id;
    }
}
