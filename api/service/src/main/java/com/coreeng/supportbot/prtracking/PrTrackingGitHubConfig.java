package com.coreeng.supportbot.prtracking;

import com.coreeng.supportbot.config.PrTrackingProps;
import com.coreeng.supportbot.github.GitHubClient;
import com.coreeng.supportbot.github.GitHubGraphQlClient;
import com.coreeng.supportbot.github.Hub4jGitHubClient;
import com.coreeng.supportbot.prtracking.source.GitHubPrSourceClient;
import com.coreeng.supportbot.prtracking.source.PrSourceClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import io.jsonwebtoken.Jwts;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.authorization.AppInstallationAuthorizationProvider;
import org.kohsuke.github.authorization.AuthorizationProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;

/**
 * Wires the GitHub adapter for PR tracking. Only activates when at least one repository uses
 * {@code provider: github} (default), so a pure-GitLab deployment can start without a GitHub
 * token. Provider-neutral wiring (the {@link com.coreeng.supportbot.prtracking.source.PrSourceClients}
 * registry itself) lives in {@link PrTrackingSourceClientsConfig}.
 */
@Configuration
@ConditionalOnProperty(name = "pr-review-tracking.enabled", havingValue = "true")
@Conditional(AnyGithubRepoCondition.class)
public class PrTrackingGitHubConfig {

    /**
     * The authorization provider shared by the REST (hub4j) and GraphQL clients. APP mode returns the
     * AppInstallationAuthorizationProvider (rotating installation token, bound when {@link #gitHub} is
     * built); TOKEN mode a static {@code Bearer} header — accepted by both the REST and GraphQL APIs.
     */
    @Bean
    public AuthorizationProvider gitHubAuthorizationProvider(PrTrackingProps props) {
        PrTrackingProps.GitHub config = props.github();
        if (config.authMode() == PrTrackingProps.AuthMode.APP) {
            PrivateKey privateKey = parsePrivateKey(config.privateKeyPem());
            long installationId = Long.parseLong(config.installationId());
            String appId = config.appId();
            AuthorizationProvider jwtProvider = () -> "Bearer " + createJwt(appId, privateKey);
            return new AppInstallationAuthorizationProvider(
                    app -> app.getInstallationById(installationId), jwtProvider);
        }
        return () -> "Bearer " + config.token();
    }

    @Bean
    public GitHub gitHub(PrTrackingProps props, AuthorizationProvider gitHubAuthorizationProvider) {
        try {
            return new GitHubBuilder()
                    .withEndpoint(props.github().apiBaseUrl())
                    .withAuthorizationProvider(gitHubAuthorizationProvider)
                    .build();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to initialize GitHub client", e);
        }
    }

    @Bean
    public GitHubClient gitHubClient(GitHub gitHub) {
        return new Hub4jGitHubClient(gitHub);
    }

    /**
     * RestClient for GitHub's GraphQL API (the REST/hub4j path exposes no {@code reviewDecision} /
     * {@code asCodeOwner}). Reuses {@link #gitHubAuthorizationProvider}; for APP mode the installation
     * token is rotated by the provider, which is bound once {@link #gitHub} is built. Jackson serialises
     * the request; the response is read as a raw String and parsed in {@link GitHubGraphQlClient}.
     */
    @Bean
    public RestClient gitHubGraphQlRestClient(
            PrTrackingProps props, AuthorizationProvider gitHubAuthorizationProvider, ObjectMapper objectMapper) {
        ClientHttpRequestInterceptor authInterceptor = (request, body, execution) -> {
            String authorization = gitHubAuthorizationProvider.getEncodedAuthorization();
            if (authorization != null) {
                request.getHeaders().set(HttpHeaders.AUTHORIZATION, authorization);
            }
            return execution.execute(request, body);
        };
        return RestClient.builder()
                .baseUrl(GitHubGraphQlClient.graphqlEndpoint(props.github().apiBaseUrl()))
                .messageConverters(ImmutableList.of(
                        new MappingJackson2HttpMessageConverter(objectMapper),
                        new StringHttpMessageConverter(StandardCharsets.UTF_8)))
                .requestInterceptor(authInterceptor)
                .build();
    }

    @Bean
    public GitHubGraphQlClient gitHubGraphQlClient(RestClient gitHubGraphQlRestClient) {
        return new GitHubGraphQlClient(gitHubGraphQlRestClient);
    }

    @Bean
    public PrSourceClient gitHubPrSourceClient(
            GitHubClient gitHubClient, GitHubGraphQlClient gitHubGraphQlClient, PrTrackingProps props) {
        return new GitHubPrSourceClient(gitHubClient, gitHubGraphQlClient, props);
    }

    private static String createJwt(String appId, PrivateKey privateKey) {
        Instant now = Instant.now();
        // Backdate iat by 60s to tolerate clock skew between the bot and GitHub.
        // Expiry is set to 9 minutes so the total window stays within GitHub's 10-minute hard limit.
        // See:
        // https://docs.github.com/en/apps/creating-github-apps/authenticating-with-a-github-app/generating-a-json-web-token-jwt-for-a-github-app
        return Jwts.builder()
                .issuer(appId)
                .issuedAt(Date.from(now.minus(60, ChronoUnit.SECONDS)))
                .expiration(Date.from(now.plus(9, ChronoUnit.MINUTES)))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    /**
     * Accepts both PKCS#1 ("BEGIN RSA PRIVATE KEY", as generated by GitHub)
     * and PKCS#8 ("BEGIN PRIVATE KEY"). BouncyCastle handles both transparently.
     *
     * {@code private-key-pem} must be provided as either a well-formed PEM string
     * or a base64-encoded PEM string.
     */
    private static PrivateKey parsePrivateKey(String pem) {
        String normalizedPem = normalizePemInput(pem);
        try (PEMParser parser = new PEMParser(new StringReader(normalizedPem))) {
            Object obj = parser.readObject();
            if (obj instanceof PEMKeyPair keyPair) {
                return new JcaPEMKeyConverter().getPrivateKey(keyPair.getPrivateKeyInfo());
            }
            if (obj instanceof org.bouncycastle.asn1.pkcs.PrivateKeyInfo keyInfo) {
                return new JcaPEMKeyConverter().getPrivateKey(keyInfo);
            }
            throw new IllegalArgumentException(
                    "Unrecognised PEM object — provide GitHub App private key as raw PEM text or a base64-encoded PEM string. "
                            + "Got: "
                            + (obj == null
                                    ? "null (no PEM header found)"
                                    : obj.getClass().getName()));
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to parse GitHub App private key from PEM", e);
        }
    }

    private static String normalizePemInput(String pem) {
        String value = pem == null ? "" : pem.trim();
        try {
            return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8).trim();
        } catch (IllegalArgumentException e) {
            return value;
        }
    }
}
