/**
 * UI-proxied OAuth callback paths. Keep in sync with
 * {@code api/service/src/main/java/com/coreeng/supportbot/security/OauthUiCallbackConstants.java}.
 */
export const OAUTH_UI_CALLBACK_PATH_PREFIX = "/api/oauth/callback/" as const;

export type OauthUiKnownProvider = "dex";

export function isOauthUiKnownProvider(
  id: string
): id is OauthUiKnownProvider {
  return id === "dex";
}
