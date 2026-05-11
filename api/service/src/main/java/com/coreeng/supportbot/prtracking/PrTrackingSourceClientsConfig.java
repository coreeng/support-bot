package com.coreeng.supportbot.prtracking;

import com.coreeng.supportbot.prtracking.source.PrSourceClient;
import com.coreeng.supportbot.prtracking.source.PrSourceClients;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provider-agnostic registration of the {@link PrSourceClients} lookup. Aggregates every
 * {@link PrSourceClient} bean registered elsewhere (GitHub adapter from
 * {@link PrTrackingGitHubConfig}, GitLab adapter from {@code PrTrackingGitLabConfig} in commit 4).
 *
 * <p>Lives in its own config rather than inside a provider-specific config so a pure-GitLab
 * (or, in principle, pure-GitHub) deployment still gets a working registry — neither provider
 * config alone is required for the bot to start.
 */
@Configuration
@ConditionalOnProperty(name = "pr-review-tracking.enabled", havingValue = "true")
public class PrTrackingSourceClientsConfig {

    @Bean
    public PrSourceClients prSourceClients(List<PrSourceClient> clients) {
        return new PrSourceClients(clients);
    }
}
