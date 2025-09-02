package com.coreeng.supportbot.testkit;

public interface MessageButtonClick {
    void preSetupMocks();

    String triggerId();
    String actionId();
    String privateMetadata();
}
