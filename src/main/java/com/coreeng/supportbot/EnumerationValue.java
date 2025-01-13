package com.coreeng.supportbot;

import com.coreeng.supportbot.slack.UIOption;

public record EnumerationValue(
    String name,
    String code
) implements UIOption {
    @Override
    public String label() {
        return name;
    }

    @Override
    public String value() {
        return code;
    }
}
