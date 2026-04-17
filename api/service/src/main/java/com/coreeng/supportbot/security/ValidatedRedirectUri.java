package com.coreeng.supportbot.security;

import java.util.Objects;

/**
 * Proof that {@link RedirectUriValidator} accepted an OAuth {@code redirect_uri}. Call sites must
 * use {@link #value()} for IdP requests — do not substitute a raw client string after validation.
 */
public sealed interface ValidatedRedirectUri permits ValidatedRedirectUri.Valid {

    /** Canonical redirect URI (ASCII, no fragment, no userinfo). */
    String value();

    /**
     * Package-private constructor: only {@link RedirectUriValidator} in this package may
     * instantiate.
     */
    final class Valid implements ValidatedRedirectUri {
        private final String value;

        Valid(String value) {
            this.value = Objects.requireNonNull(value, "value");
        }

        @Override
        public String value() {
            return value;
        }
    }
}
