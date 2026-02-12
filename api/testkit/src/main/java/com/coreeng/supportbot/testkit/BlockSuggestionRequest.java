package com.coreeng.supportbot.testkit;

public interface BlockSuggestionRequest {
    String actionId();

    String value();

    String viewType();

    String privateMetadata();

    String callbackId();
}
