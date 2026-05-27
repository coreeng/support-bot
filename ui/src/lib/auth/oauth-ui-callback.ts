/**
 * UI-proxied OAuth callback paths. Keep in sync with
 * {@code api/service/src/main/java/com/coreeng/supportbot/security/OauthUiCallbackConstants.java}.
 */
export const OAUTH_UI_CALLBACK_PATH_PREFIX = "/api/oauth/callback/" as const;

export const OAUTH_UI_KNOWN_PROVIDERS = ["google", "azure", "dex"] as const;

export type OauthUiKnownProvider = (typeof OAUTH_UI_KNOWN_PROVIDERS)[number];

export function isOauthUiKnownProvider(id: string): id is OauthUiKnownProvider {
  return (OAUTH_UI_KNOWN_PROVIDERS as readonly string[]).includes(id);
}
