package com.coreeng.supportbot.escalation.command;

import lombok.Getter;

import java.util.regex.Pattern;

@Getter
public enum CommandButton {
    escalate("command-escalate");

    public final static Pattern pattern = Pattern.compile("^command-.*$");

    private final String actionId;

    CommandButton(String label) {
        this.actionId = label;
    }

    public static CommandButton valueOfOrNull(String actionId) {
        for (CommandButton v : values()) {
            if (v.actionId().equals(actionId)) {
                return v;
            }
        }
        return null;
    }
}
