package com.coreeng.supportbot.testkit;

import lombok.Builder;
import lombok.Getter;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Builder
@Getter
public class RawButtonClick {
    @NonNull private final String teamId;

    @NonNull private final String userId;

    @NonNull private final String actionId;

    @NonNull private final String triggerId;

    @NonNull private final String privateMetadata;

    /** Message the button lives on; required by handlers that resolve the thread from the click payload. */
    @Nullable private final String channelId;

    @Nullable private final MessageTs messageTs;

    @Nullable private final MessageTs threadTs;
}
