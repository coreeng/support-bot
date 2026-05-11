package com.coreeng.supportbot.prtracking.source;

import org.jspecify.annotations.Nullable;

/**
 * Thrown by {@link PrSourceClient} implementations for any provider-side failure
 * (not found, auth, server errors, network). Adapters wrap their provider-specific
 * exception types into this.
 */
public class PrSourceException extends RuntimeException {
    public PrSourceException(@Nullable String message) {
        super(message);
    }

    public PrSourceException(@Nullable String message, Throwable cause) {
        super(message, cause);
    }
}
