package com.coreeng.supportbot.security;

import com.coreeng.supportbot.teams.SupportTeamService;
import com.coreeng.supportbot.teams.Team;
import com.coreeng.supportbot.teams.TeamService;
import com.coreeng.supportbot.teams.TeamType;
import com.google.common.collect.ImmutableList;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class OAuthExchangeService {
    private static final Pattern LEADERSHIP_PATTERN = Pattern.compile("leadership", Pattern.CASE_INSENSITIVE);
    private static final Pattern SUPPORT_PATTERN = Pattern.compile("support", Pattern.CASE_INSENSITIVE);
    private static final Pattern ESCALATION_PATTERN = Pattern.compile("escalation", Pattern.CASE_INSENSITIVE);

    private final ClientRegistrationRepository clientRegistrationRepository;
    private final RestTemplate restTemplate;
    private final JwtService jwtService;
    private final TeamService teamService;
    private final SupportTeamService supportTeamService;
    private final AllowListService allowListService;
    private final JwtGroupTeamMerger jwtGroupTeamMerger;
    private final RedirectUriValidator redirectUriValidator;
    private final OAuthStateStore oauthStateStore;

    public String exchangeCodeForToken(String provider, String code, String redirectUri, @Nullable String state) {
        if (!oauthStateStore.consumeIfValid(state)) {
            throw new IllegalArgumentException("Invalid or expired OAuth state parameter");
        }
        redirectUriValidator.validate(redirectUri);

        var registration = clientRegistrationRepository.findByRegistrationId(provider);
        if (registration == null) {
            throw new IllegalArgumentException("Unknown OAuth provider: " + provider);
        }

        // Exchange authorization code for access token
        var tokenUri = registration.getProviderDetails().getTokenUri();
        var clientId = registration.getClientId();
        var clientSecret = registration.getClientSecret();

        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("code", code);
        body.add("redirect_uri", redirectUri);
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);

        var request = new HttpEntity<>(body, headers);

        try {
            var response = restTemplate.postForObject(tokenUri, request, Map.class);
            if (response == null || !response.containsKey("access_token")) {
                throw new IllegalStateException("Token exchange failed: no access_token in response");
            }

            var accessToken = (String) response.get("access_token");

            // Fetch user info
            var userInfoUri =
                    registration.getProviderDetails().getUserInfoEndpoint().getUri();
            var userInfoHeaders = new HttpHeaders();
            userInfoHeaders.setBearerAuth(accessToken);
            var userInfoRequest = new HttpEntity<>(userInfoHeaders);

            var userInfoResponse = restTemplate.exchange(
                    userInfoUri, org.springframework.http.HttpMethod.GET, userInfoRequest, Map.class);
            if (!userInfoResponse.getStatusCode().is2xxSuccessful() || userInfoResponse.getBody() == null) {
                throw new IllegalStateException("Failed to fetch user info");
            }

            var userInfo = new HashMap<>(userInfoResponse.getBody());

            // Merge ID token claims (Azure's userinfo endpoint often omits email/preferred_username).
            // The token is verified against the provider's JWKS and iss/aud claims are checked.
            var idToken = (String) response.get("id_token");
            if (idToken != null) {
                try {
                    var claims = verifyAndExtractClaims(idToken, registration);
                    claims.forEach(userInfo::putIfAbsent);
                    if (claims.containsKey("groups")) {
                        userInfo.put("groups", claims.get("groups"));
                    }
                } catch (Exception e) {
                    if (requiresIdTokenClaims(provider)) {
                        throw new IllegalStateException(
                                "ID token verification failed for provider "
                                        + registration.getRegistrationId()
                                        + " which requires id_token claims (e.g. LDAP groups)",
                                e);
                    }
                    log.error(
                            "ID token verification failed for provider {} — skipping id_token claims",
                            registration.getRegistrationId(),
                            e);
                }
            }

            // Extract email and name
            var email = extractEmail(userInfo);
            if (!allowListService.isAllowed(email)) {
                log.warn("Allow-list rejected user during OAuth exchange");
                throw new UserNotAllowedException();
            }
            var name = extractName(userInfo);

            log.info("OAuth2 login successful for user");

            // Compute roles (Dex: merge LDAP groups claim into email-based platform teams)
            var teams =
                    jwtGroupTeamMerger.mergeForProvider(provider, userInfo, teamService.listTeamsByUserEmail(email));
            var roles = computeRoles(email, teams);

            var principal = new UserPrincipal(email, name, teams, roles);

            // Generate JWT
            return jwtService.generateToken(principal);
        } catch (RestClientException e) {
            log.error("OAuth token exchange failed", e);
            throw new IllegalStateException("OAuth token exchange failed", e);
        }
    }

    private String extractEmail(Map<String, Object> userInfo) {
        var email = userInfo.get("email");
        if (email != null) {
            return email.toString().toLowerCase(Locale.ROOT);
        }
        var preferredUsername = userInfo.get("preferred_username");
        if (preferredUsername != null) {
            return preferredUsername.toString().toLowerCase(Locale.ROOT);
        }
        throw new IllegalStateException("Unable to extract email from OAuth2 user");
    }

    private String extractName(Map<String, Object> userInfo) {
        var name = userInfo.get("name");
        if (name != null) {
            return name.toString();
        }
        var givenName = userInfo.get("given_name");
        var familyName = userInfo.get("family_name");
        if (givenName != null || familyName != null) {
            return ((givenName != null ? givenName : "") + " " + (familyName != null ? familyName : "")).trim();
        }
        return extractEmail(userInfo);
    }

    private ImmutableList<Role> computeRoles(String email, ImmutableList<Team> teams) {
        var roles = ImmutableList.<Role>builder();
        roles.add(Role.USER);

        if (computeIsLeadership(email, teams)) {
            roles.add(Role.LEADERSHIP);
        }
        if (computeIsSupportEngineer(email, teams)) {
            roles.add(Role.SUPPORT_ENGINEER);
        }
        if (computeIsEscalation(teams)) {
            roles.add(Role.ESCALATION);
        }

        return roles.build();
    }

    private boolean computeIsLeadership(String email, ImmutableList<Team> teams) {
        return supportTeamService.isLeadershipMemberByUserEmail(email) || hasTeamType(teams, LEADERSHIP_PATTERN);
    }

    private boolean computeIsSupportEngineer(String email, ImmutableList<Team> teams) {
        return supportTeamService.isMemberByUserEmail(email) || hasTeamType(teams, SUPPORT_PATTERN);
    }

    private boolean computeIsEscalation(ImmutableList<Team> teams) {
        return hasTeamType(teams, ESCALATION_PATTERN);
    }

    private boolean hasTeamType(ImmutableList<Team> teams, Pattern pattern) {
        return teams.stream()
                .flatMap(t -> t.types().stream())
                .map(TeamType::name)
                .anyMatch(type -> pattern.matcher(type).find());
    }

    /** Dex relies on ID token for LDAP group claims — verification failure must be fatal. */
    private static boolean requiresIdTokenClaims(String provider) {
        return "dex".equalsIgnoreCase(provider);
    }

    /**
     * Verifies the ID token signature against the provider's JWKS and validates {@code iss} and
     * {@code aud} claims per the OIDC spec.
     *
     * @return verified claims, or empty map if JWKS URI is not configured (claims are never trusted
     *     without verification)
     */
    private Map<String, Object> verifyAndExtractClaims(String idToken, ClientRegistration registration)
            throws Exception {
        String jwksUri = registration.getProviderDetails().getJwkSetUri();
        if (jwksUri == null || jwksUri.isBlank()) {
            log.warn(
                    "No JWKS URI for provider {} — skipping ID token claims (unverified tokens are never trusted)",
                    registration.getRegistrationId());
            return Map.of();
        }

        JWKSource<SecurityContext> jwkSource =
                JWKSourceBuilder.create(URI.create(jwksUri).toURL()).build();
        var jwtProcessor = new DefaultJWTProcessor<SecurityContext>();

        var keySelector = new JWSVerificationKeySelector<>(
                Set.of(JWSAlgorithm.RS256, JWSAlgorithm.RS384, JWSAlgorithm.RS512, JWSAlgorithm.ES256), jwkSource);
        jwtProcessor.setJWSKeySelector(keySelector);

        var expectedClaimsBuilder = new JWTClaimsSet.Builder().audience(registration.getClientId());
        String expectedIssuer = registration.getProviderDetails().getIssuerUri();
        if (expectedIssuer != null && !expectedIssuer.isBlank()) {
            expectedClaimsBuilder.issuer(expectedIssuer);
        }
        var claimsVerifier = new DefaultJWTClaimsVerifier<SecurityContext>(
                expectedClaimsBuilder.build(), new HashSet<>(Set.of("iss", "sub", "iat", "exp")));
        jwtProcessor.setJWTClaimsSetVerifier(claimsVerifier);

        JWTClaimsSet verified = jwtProcessor.process(idToken, null);
        return verified.getClaims();
    }
}
