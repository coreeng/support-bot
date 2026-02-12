package com.coreeng.supportbot.testkit;

public interface ViewSubmissionResponseReceiver<T> {
    T parse(String responseBody);
}
