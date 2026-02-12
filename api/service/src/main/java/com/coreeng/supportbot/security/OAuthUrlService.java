package com.coreeng.supportbot.security;

import java.util.Optional;
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

    public Optional<String> getAuthorizationUrl(String provider, String redirectUri) {
        var registration = clientRegistrationRepository.findByRegistrationId(provider);
        if (registration == null) {
            log.warn("Unknown OAuth provider: {}", provider);
            return Optional.empty();
        }

        var authorizationUri = registration.getProviderDetails().getAuthorizationUri();
        var clientId = registration.getClientId();
        var scopes = String.join(" ", registration.getScopes());
        var state = UUID.randomUUID().toString();

        var url = UriComponentsBuilder.fromUriString(authorizationUri)
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", scopes)
                .queryParam("state", state)
                .build()
                .toUriString();

        return Optional.of(url);
    }
}
