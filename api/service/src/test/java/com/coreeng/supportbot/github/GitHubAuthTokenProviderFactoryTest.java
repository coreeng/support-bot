package com.coreeng.supportbot.github;

import static org.assertj.core.api.Assertions.assertThat;

import com.coreeng.supportbot.config.PrTrackingAuthMode;
import com.coreeng.supportbot.config.PrTrackingGitHubProps;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class GitHubAuthTokenProviderFactoryTest {

    @Test
    void createsTokenModeProviderAndReturnsConfiguredToken() {
        PrTrackingGitHubProps githubConfig =
                new PrTrackingGitHubProps(PrTrackingAuthMode.TOKEN, "https://api.github.com", "pat-123", "", "", "");

        GitHubAuthTokenProvider provider = GitHubAuthTokenProviderFactory.create(
                githubConfig,
                (baseUrl, appId, installationId, privateKeyPem) ->
                        new GitHubInstallationToken("unused", Instant.parse("2030-01-01T00:00:00Z")),
                Clock.systemUTC());

        assertThat(provider).isInstanceOf(TokenModeGitHubAuthTokenProvider.class);
        assertThat(provider.getToken()).isEqualTo("pat-123");
    }

    @Test
    void createsAppModeProviderAndCachesUntilRefreshWindow() {
        PrTrackingGitHubProps githubConfig = new PrTrackingGitHubProps(
                PrTrackingAuthMode.APP,
                "https://api.github.com",
                "",
                "app-id",
                "installation-id",
                "private-key");
        MutableClock clock = new MutableClock(Instant.parse("2026-02-26T10:00:00Z"));
        AtomicInteger fetchCount = new AtomicInteger(0);

        GitHubAppInstallationTokenClient tokenClient = (baseUrl, appId, installationId, privateKeyPem) -> {
            int call = fetchCount.incrementAndGet();
            return new GitHubInstallationToken("app-token-" + call, clock.instant().plusSeconds(3600));
        };

        GitHubAuthTokenProvider provider = GitHubAuthTokenProviderFactory.create(githubConfig, tokenClient, clock);
        assertThat(provider).isInstanceOf(AppModeGitHubAuthTokenProvider.class);

        String firstToken = provider.getToken();
        String secondToken = provider.getToken();
        assertThat(firstToken).isEqualTo("app-token-1");
        assertThat(secondToken).isEqualTo("app-token-1");
        assertThat(fetchCount.get()).isEqualTo(1);

        clock.advanceBySeconds(3301);
        String refreshedToken = provider.getToken();
        assertThat(refreshedToken).isEqualTo("app-token-2");
        assertThat(fetchCount.get()).isEqualTo(2);
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant initialNow) {
            this.now = initialNow;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }

        private void advanceBySeconds(long seconds) {
            now = now.plusSeconds(seconds);
        }
    }
}
