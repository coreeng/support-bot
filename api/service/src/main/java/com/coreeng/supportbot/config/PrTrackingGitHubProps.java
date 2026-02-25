package com.coreeng.supportbot.config;

public record PrTrackingGitHubProps(
        PrTrackingAuthMode authMode,
        String apiBaseUrl,
        String token,
        String appId,
        String installationId,
        String privateKeyPem) {

    public PrTrackingGitHubProps {
        authMode = authMode == null ? PrTrackingAuthMode.TOKEN : authMode;
        apiBaseUrl = nonNullString(apiBaseUrl);
        token = nonNullString(token);
        appId = nonNullString(appId);
        installationId = nonNullString(installationId);
        privateKeyPem = nonNullString(privateKeyPem);
    }

    public static PrTrackingGitHubProps defaultTokenModeConfig() {
        return new PrTrackingGitHubProps(PrTrackingAuthMode.TOKEN, "", "", "", "", "");
    }

    private static String nonNullString(String value) {
        return value == null ? "" : value;
    }
}
