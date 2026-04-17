package com.coreeng.supportbot.security;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Fails startup when {@code UI_ORIGIN} is unset outside local-like profiles, but only if at least
 * one OAuth2 login provider is fully configured (UI-driven SSO is possible). With no OAuth2 clients,
 * proxied OAuth endpoints are disabled — {@code UI_ORIGIN} is not required.
 *
 * <p>When OAuth2 is configured: local-like means every activated profile is either the Spring
 * {@code default} profile or one of the named dev/test profiles below. If {@link
 * Environment#getActiveProfiles()} is empty, the same check is applied to the comma-separated {@code
 * spring.profiles.active} property (when unset or blank, no explicit profiles — typical {@code
 * java -jar} without {@code SPRING_PROFILES_ACTIVE}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuthUiOriginStartupWarning {

    private static final Set<String> LOCAL_LIKE_PROFILES =
            Set.of("local", "test", "functionaltests", "integrationtests", "integrationtests-oidc");

    private final Environment environment;
    private final OAuth2AvailabilityChecker oauth2AvailabilityChecker;

    @EventListener(ApplicationReadyEvent.class)
    public void warnIfUiOriginUnsetOutsideLocalProfiles() {
        if (!oauth2AvailabilityChecker.isOAuth2Available()) {
            return;
        }
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
                + "another local-like profile), or omit SPRING_PROFILES_ACTIVE / spring.profiles.active. "
                + "See api/service/docs/configuration.md (SSO, UI origin contract).";
        log.error(message);
        throw new IllegalStateException(message);
    }

    private boolean isLocalLikeProfile() {
        String[] rawActive = environment.getActiveProfiles();
        String[] active = rawActive == null ? new String[0] : rawActive;
        if (active.length > 0) {
            return Arrays.stream(active).allMatch(this::isLocalLikeOrDefaultName);
        }
        String declared = environment.getProperty("spring.profiles.active");
        if (declared == null || declared.isBlank()) {
            return true;
        }
        List<String> names = Arrays.stream(declared.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        if (names.isEmpty()) {
            return true;
        }
        return names.stream().allMatch(this::isLocalLikeOrDefaultName);
    }

    private boolean isLocalLikeOrDefaultName(String profile) {
        if ("default".equalsIgnoreCase(profile)) {
            return true;
        }
        return LOCAL_LIKE_PROFILES.stream().anyMatch(l -> l.equalsIgnoreCase(profile));
    }
}
