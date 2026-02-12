package com.coreeng.supportbot.config;

import com.coreeng.supportbot.slack.UIOption;

public interface EnumerationValue extends UIOption {
    @Override
    String label();

    String code();

    @Override
    default String value() {
        return code();
    }
}
