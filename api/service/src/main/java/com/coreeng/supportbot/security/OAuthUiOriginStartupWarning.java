package com.coreeng.supportbot.security;

import java.util.Arrays;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Fails startup when {@code UI_ORIGIN} is unset outside local-like profiles. The API validates UI
 * OAuth {@code redirect_uri} against the origin from {@code security.oauth2.redirect-uri}, which
 * defaults from {@code UI_ORIGIN}; it must match the Next.js {@code NEXTAUTH_URL} origin.
 *
 * <p>Also treated as local-like: no explicitly activated profiles (typical {@code java -jar} with no
 * {@code -Dspring.profiles.active}) and the {@code test} profile (Spring Boot tests).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuthUiOriginStartupWarning {

    private static final Set<String> LOCAL_LIKE_PROFILES =
            Set.of("local", "test", "functionaltests", "integrationtests", "integrationtests-oidc");

    private final Environment environment;

    @EventListener(ApplicationReadyEvent.class)
    public void warnIfUiOriginUnsetOutsideLocalProfiles() {
        if (isLocalLikeProfile()) {
            return;
        }
        String uiOrigin = environment.getProperty("UI_ORIGIN");
        if (uiOrigin != null && !uiOrigin.isBlank()) {
            return;
        }
        String message = "UI_ORIGIN is not set. security.oauth2.redirect-uri defaults to http://localhost:3000/login; "
                + "the API allows UI redirect_uri only if its origin matches that value. Set UI_ORIGIN to your "
                + "public UI base URL (scheme + host + port, no path) so it matches the origin of NEXTAUTH_URL "
                + "on the Next.js app — otherwise /auth/oauth-url and /auth/oauth/exchange return 400. "
                + "For local development without setting UI_ORIGIN, use spring.profiles.active=local (or "
                + "another local-like profile), or run with no explicitly active Spring profiles. "
                + "See api/service/docs/configuration.md (SSO, UI origin contract).";
        log.error(message);
        throw new IllegalStateException(message);
    }

    private boolean isLocalLikeProfile() {
        String[] active = environment.getActiveProfiles();
        if (active.length == 0) {
            return true;
        }
        return Arrays.stream(active).anyMatch(p -> LOCAL_LIKE_PROFILES.stream().anyMatch(l -> l.equalsIgnoreCase(p)));
    }
}
