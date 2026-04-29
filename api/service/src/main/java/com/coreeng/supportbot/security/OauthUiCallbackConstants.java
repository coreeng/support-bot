package com.coreeng.supportbot.security;

import java.util.Set;

/**
 * Paths and provider ids for UI-proxied OAuth callbacks. Keep in sync with {@code
 * ui/src/lib/auth/oauth-ui-callback.ts}.
 */
public final class OauthUiCallbackConstants {

    private OauthUiCallbackConstants() {}

    public static final String CALLBACK_PATH_PREFIX = "/api/oauth/callback/";

    public static final Set<String> KNOWN_PROVIDERS = Set.of("dex");
}
