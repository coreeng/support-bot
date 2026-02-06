package com.coreeng.supportbot.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;

import java.util.ArrayList;

@Slf4j
@Configuration
public class OAuth2ClientConfig {

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository(
            @Value("${spring.security.oauth2.client.registration.google.client-id:}") String googleClientId,
            @Value("${spring.security.oauth2.client.registration.google.client-secret:}") String googleClientSecret,
            @Value("${spring.security.oauth2.client.registration.azure.client-id:}") String azureClientId,
            @Value("${spring.security.oauth2.client.registration.azure.client-secret:}") String azureClientSecret,
            @Value("${AZURE_AD_TENANT_ID:common}") String azureTenantId
    ) {
        var registrations = new ArrayList<ClientRegistration>();

        if (isNotBlank(googleClientId) && isNotBlank(googleClientSecret)) {
            registrations.add(googleClientRegistration(googleClientId, googleClientSecret));
            log.info("Google OAuth2 client registered");
        }

        if (isNotBlank(azureClientId) && isNotBlank(azureClientSecret)) {
            registrations.add(azureClientRegistration(azureClientId, azureClientSecret, azureTenantId));
            log.info("Azure AD OAuth2 client registered");
        }

        if (registrations.isEmpty()) {
            log.info("No OAuth2 clients registered - credentials not provided");
            // Return empty repository - OAuth2 login will be disabled
            return registrationId -> null;
        }

        return new InMemoryClientRegistrationRepository(registrations);
    }

    private ClientRegistration googleClientRegistration(String clientId, String clientSecret) {
        return ClientRegistration.withRegistrationId("google")
            .clientId(clientId)
            .clientSecret(clientSecret)
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
            .scope("openid", "email", "profile")
            .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
            .tokenUri("https://oauth2.googleapis.com/token")
            .userInfoUri("https://www.googleapis.com/oauth2/v3/userinfo")
            .userNameAttributeName(IdTokenClaimNames.SUB)
            .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
            .clientName("Google")
            .build();
    }

    private ClientRegistration azureClientRegistration(String clientId, String clientSecret, String tenantId) {
        String baseUri = "https://login.microsoftonline.com/" + tenantId;
        return ClientRegistration.withRegistrationId("azure")
            .clientId(clientId)
            .clientSecret(clientSecret)
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
            .scope("openid", "email", "profile")
            .authorizationUri(baseUri + "/oauth2/v2.0/authorize")
            .tokenUri(baseUri + "/oauth2/v2.0/token")
            .jwkSetUri(baseUri + "/discovery/v2.0/keys")
            .userInfoUri("https://graph.microsoft.com/oidc/userinfo")
            .userNameAttributeName(IdTokenClaimNames.SUB)
            .clientName("Azure AD")
            .build();
    }

    private static boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}
