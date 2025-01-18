package com.coreeng.supportbot.ticket;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import javax.annotation.Nullable;
import java.util.regex.Pattern;

@Getter
@RequiredArgsConstructor
public enum TicketViewType {
    summary("ticket-summary"),
    escalate("ticket-escalate"),
    confirmClose("ticket-confirm-close");

    public static final Pattern namePattern = Pattern.compile("^ticket-.*$");

    private final String callbackId;

    @Nullable
    public static TicketViewType fromCallbackIdOrNull(String callbackId) {
        for (TicketViewType value : values()) {
            if (value.callbackId.equals(callbackId)) {
                return value;
            }
        }
        return null;
    }
}
