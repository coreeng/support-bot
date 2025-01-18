package com.coreeng.supportbot.config;

import com.coreeng.supportbot.slack.UIOption;

public interface EnumerationValue extends UIOption {
    String name();
    String code();

    @Override
    default String label() {
        return name();
    }

    @Override
    default String value() {
        return code();
    }
}
