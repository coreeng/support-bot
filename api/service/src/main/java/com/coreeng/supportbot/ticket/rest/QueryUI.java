package com.coreeng.supportbot.ticket.rest;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

@Getter
@Builder(toBuilder = true)
@Jacksonized
public class QueryUI {
    private String message;
}
