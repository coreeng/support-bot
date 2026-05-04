package com.coreeng.supportbot.security;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Service
@RequiredArgsConstructor
public class OAuthUrlService {
    private final ClientRegistrationRepository clientRegistrationRepository;
    private final RedirectUriValidator redirectUriValidator;

    public record AuthorizationUrlResult(String url, String state) {}

    /**
     * @throws IllegalArgumentException if the redirect URI is invalid
     * @throws IllegalStateException if the Dex OAuth2 client is not configured
     */
    public AuthorizationUrlResult getAuthorizationUrl(String redirectUri) {
        ValidatedRedirectUri validatedRedirectUri = redirectUriValidator.validate(redirectUri);
        var registration = clientRegistrationRepository.findByRegistrationId("dex");
        if (registration == null) {
            throw new IllegalStateException("Dex OAuth2 client is not configured");
        }

        var authorizationUri = registration.getProviderDetails().getAuthorizationUri();
        var clientId = registration.getClientId();
        var scopes = String.join(" ", registration.getScopes());
        // State is verified client-side (cookie) — server-side store requires shared cache (Redis)
        // which is not yet available. TODO: add server-side state verification when Redis is introduced.
        var state = UUID.randomUUID().toString();

        var url = UriComponentsBuilder.fromUriString(authorizationUri)
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", validatedRedirectUri.value())
                .queryParam("response_type", "code")
                .queryParam("scope", scopes)
                .queryParam("state", state)
                .build()
                .toUriString();

        return new AuthorizationUrlResult(url, state);
    }
}
