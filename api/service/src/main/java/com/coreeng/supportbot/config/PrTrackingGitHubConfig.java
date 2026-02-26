package com.coreeng.supportbot.config;

import com.coreeng.supportbot.github.GitHubAppInstallationTokenClient;
import com.coreeng.supportbot.github.GitHubAuthTokenProvider;
import com.coreeng.supportbot.github.GitHubAuthTokenProviderFactory;
import com.coreeng.supportbot.github.GitHubClient;
import com.coreeng.supportbot.github.RestClientGitHubClient;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@ConditionalOnProperty(name = "pr-review-tracking.enabled", havingValue = "true")
public class PrTrackingGitHubConfig {

    @Bean
    public Clock gitHubClock() {
        return Clock.systemUTC();
    }

    @Bean
    public GitHubAuthTokenProvider gitHubAuthTokenProvider(
            PrTrackingProps props,
            ObjectProvider<GitHubAppInstallationTokenClient> appTokenClientProvider,
            @Qualifier("gitHubClock") Clock clock) {
        return GitHubAuthTokenProviderFactory.create(
                props.github(), appTokenClientProvider.getIfAvailable(), clock);
    }

    @Bean
    public RestClient gitHubRestClient(PrTrackingProps props) {
        return RestClient.builder().baseUrl(props.github().apiBaseUrl()).build();
    }

    @Bean
    public GitHubClient gitHubClient(
            @Qualifier("gitHubRestClient") RestClient gitHubRestClient, GitHubAuthTokenProvider tokenProvider) {
        return new RestClientGitHubClient(gitHubRestClient, tokenProvider);
    }
}
