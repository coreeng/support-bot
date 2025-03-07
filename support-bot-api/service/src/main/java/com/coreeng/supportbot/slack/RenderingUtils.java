package com.coreeng.supportbot.slack;

import com.slack.api.model.block.composition.OptionObject;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.slack.api.model.block.composition.BlockCompositions.plainText;

public class RenderingUtils {
    private RenderingUtils() {
    }

    public static OptionObject toOptionObject(UIOption option) {
        checkNotNull(option);
        return OptionObject.builder()
            .text(plainText(option.label()))
            .value(option.value())
            .build();
    }

    public static OptionObject toOptionObjectOrNull(@Nullable UIOption option) {
        return option != null
            ? toOptionObject(option)
            : null;
    }
}
