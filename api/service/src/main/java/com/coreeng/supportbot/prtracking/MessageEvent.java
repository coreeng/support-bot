package com.coreeng.supportbot.prtracking;

import com.coreeng.supportbot.config.PrTrackingProps;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * Each constant carries an extractor so {@code compileAll} can loop over {@code values()} rather
 * than listing every event by name. Adding a new constant requires providing an extractor and a
 * matching field on {@link PrTrackingProps.Messages}, both enforced at compile time. The loop in
 * {@code compileAll} then picks it up automatically, no third change needed there.
 */
public enum MessageEvent {
    DETECTED(PrTrackingProps.Messages::detected),
    ESCALATED(PrTrackingProps.Messages::escalated),
    APPROVED(PrTrackingProps.Messages::approved),
    CHANGES_REQUESTED(PrTrackingProps.Messages::changesRequested),
    MERGED(PrTrackingProps.Messages::merged),
    CLOSED(PrTrackingProps.Messages::closed);

    private final Function<PrTrackingProps.Messages, @Nullable String> extractor;

    MessageEvent(Function<PrTrackingProps.Messages, @Nullable String> extractor) {
        this.extractor = extractor;
    }

    public @Nullable String extract(PrTrackingProps.Messages messages) {
        return extractor.apply(messages);
    }
}
