package com.coreeng.supportbot.slack;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.slack.api.model.block.composition.BlockCompositions.plainText;

import com.slack.api.model.block.composition.OptionObject;
import org.jspecify.annotations.Nullable;

public class RenderingUtils {
    private RenderingUtils() {}

    public static OptionObject toOptionObject(UIOption option) {
        checkNotNull(option);
        return OptionObject.builder()
                .text(plainText(option.label()))
                .value(option.value())
                .build();
    }

    @Nullable public static OptionObject toOptionObjectOrNull(@Nullable UIOption option) {
        return option != null ? toOptionObject(option) : null;
    }
}
