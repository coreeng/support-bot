package com.coreeng.supportbot.escalation;

import lombok.Getter;

import javax.annotation.Nullable;
import java.util.regex.Pattern;

@Getter
public enum EscalationOperation {
    confirm("escalation-confirm"),
    changeTopic("escalation-topic"),
    changeTeam("escalation-team");

    public static final Pattern pattern = Pattern.compile("^escalation-.*$");

    private final String actionId;

    EscalationOperation(String actionId) {
        this.actionId = actionId;
    }

    @Nullable
    public static EscalationOperation fromActionIdOrNull(String actionId) {
        for (EscalationOperation value : values()) {
            if (value.actionId.equals(actionId)) {
                return value;
            }
        }
        return null;
    }
}
