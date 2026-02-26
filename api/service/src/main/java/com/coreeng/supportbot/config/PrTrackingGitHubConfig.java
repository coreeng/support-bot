package com.coreeng.supportbot.config;

import com.coreeng.supportbot.github.GitHubClient;
import com.coreeng.supportbot.github.Hub4jGitHubClient;
import io.jsonwebtoken.Jwts;
import java.io.IOException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.authorization.AppInstallationAuthorizationProvider;
import org.kohsuke.github.authorization.AuthorizationProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "pr-review-tracking.enabled", havingValue = "true")
public class PrTrackingGitHubConfig {

    @Bean
    public GitHub gitHub(PrTrackingProps props) {
        PrTrackingGitHubProps config = props.github();
        try {
            if (config.authMode() == PrTrackingAuthMode.APP) {
                return buildAppModeClient(config);
            }
            return new GitHubBuilder()
                    .withEndpoint(config.apiBaseUrl())
                    .withOAuthToken(config.token())
                    .build();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to initialize GitHub client", e);
        }
    }

    @Bean
    public GitHubClient gitHubClient(GitHub gitHub) {
        return new Hub4jGitHubClient(gitHub);
    }

    private static GitHub buildAppModeClient(PrTrackingGitHubProps config) throws IOException {
        PrivateKey privateKey = parsePrivateKey(config.privateKeyPem());
        long installationId = Long.parseLong(config.installationId());
        String appId = config.appId();

        AuthorizationProvider jwtProvider = () -> "Bearer " + createJwt(appId, privateKey);

        AppInstallationAuthorizationProvider authProvider = new AppInstallationAuthorizationProvider(
                app -> app.getInstallationById(installationId), jwtProvider);

        return new GitHubBuilder()
                .withEndpoint(config.apiBaseUrl())
                .withAuthorizationProvider(authProvider)
                .build();
    }

    private static String createJwt(String appId, PrivateKey privateKey) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(appId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(10, ChronoUnit.MINUTES)))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    private static PrivateKey parsePrivateKey(String pem) {
        String base64 = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] keyBytes = Base64.getDecoder().decode(base64);
        try {
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Failed to parse private key. Key must be in PKCS#8 PEM format (-----BEGIN PRIVATE KEY-----). "
                            + "Convert PKCS#1 with: openssl pkcs8 -topk8 -inform PEM -outform PEM -nocrypt -in key.pem -out key-pkcs8.pem",
                    e);
        }
    }
}
