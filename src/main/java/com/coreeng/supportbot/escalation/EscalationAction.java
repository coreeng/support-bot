package com.coreeng.supportbot.escalation;

import lombok.Getter;

import javax.annotation.Nullable;
import java.util.regex.Pattern;

@Getter
public enum EscalationAction {
    confirm("escalation-confirm"),
    changeTopic("escalation-topic"),
    changeTeam("escalation-team");

    public static final Pattern pattern = Pattern.compile("^escalation-.*$");

    private final String actionId;

    EscalationAction(String actionId) {
        this.actionId = actionId;
    }

    @Nullable
    public static EscalationAction valueOfOrNull(String actionId) {
        for (EscalationAction value : values()) {
            if (value.actionId.equals(actionId)) {
                return value;
            }
        }
        return null;
    }
}
