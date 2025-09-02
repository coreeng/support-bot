package com.coreeng.supportbot.testkit;

import lombok.Builder;
import lombok.Getter;
import org.jspecify.annotations.NonNull;

@Builder
@Getter
public class ViewsOpenExpectation<T> {
    private final StubWithResult.@NonNull Receiver<T> receiver;

    @NonNull
    private final String triggerId;
    @NonNull
    private final String viewCallbackId;
    @NonNull
    @Builder.Default
    private final String viewType = "modal";
}
