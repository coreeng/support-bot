package com.coreeng.supportbot.escalation;

import java.util.regex.Pattern;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

@Getter
public enum EscalationOperation {
    resolve("escalation-resolve");

    public static final Pattern PATTERN = Pattern.compile("^escalation-.*$");

    private final String actionId;

    EscalationOperation(String actionId) {
        this.actionId = actionId;
    }

    @Nullable public static EscalationOperation fromActionIdOrNull(String actionId) {
        for (EscalationOperation value : values()) {
            if (value.actionId.equals(actionId)) {
                return value;
            }
        }
        return null;
    }
}
