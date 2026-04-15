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
    private final OAuthStateStore oauthStateStore;

    public record AuthorizationUrlResult(String url, String state) {}

    /**
     * @throws IllegalArgumentException if the redirect URI is invalid
     * @throws IllegalArgumentException if the provider is unknown
     */
    public AuthorizationUrlResult getAuthorizationUrl(String provider, String redirectUri) {
        redirectUriValidator.validate(redirectUri);
        var registration = clientRegistrationRepository.findByRegistrationId(provider);
        if (registration == null) {
            throw new IllegalArgumentException("Unknown OAuth provider: " + provider);
        }

        var authorizationUri = registration.getProviderDetails().getAuthorizationUri();
        var clientId = registration.getClientId();
        var scopes = String.join(" ", registration.getScopes());
        var state = UUID.randomUUID().toString();
        oauthStateStore.store(state);

        var url = UriComponentsBuilder.fromUriString(authorizationUri)
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", scopes)
                .queryParam("state", state)
                .build()
                .toUriString();

        return new AuthorizationUrlResult(url, state);
    }
}
