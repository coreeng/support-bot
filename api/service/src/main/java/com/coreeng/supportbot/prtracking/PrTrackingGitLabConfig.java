package com.coreeng.supportbot.prtracking;

import com.coreeng.supportbot.config.PrTrackingProps;
import com.coreeng.supportbot.prtracking.source.GitLabGroupMemberCache;
import com.coreeng.supportbot.prtracking.source.GitLabPrSourceClient;
import com.coreeng.supportbot.prtracking.source.PrSourceClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;

/**
 * Wires the GitLab adapter for PR tracking. Only activates when at least one repository uses
 * {@code provider: gitlab}, so pure-GitHub deployments don't pay the cost of an unused RestClient
 * + caches and aren't forced to supply a GitLab token they'll never use.
 *
 * <p>Provider-neutral wiring (the {@link com.coreeng.supportbot.prtracking.source.PrSourceClients}
 * registry itself) lives in {@link PrTrackingSourceClientsConfig}, which picks this adapter up
 * automatically via {@link PrSourceClient}-typed bean aggregation.
 */
@Configuration
@ConditionalOnProperty(name = "pr-review-tracking.enabled", havingValue = "true")
@Conditional(AnyGitLabRepoCondition.class)
public class PrTrackingGitLabConfig {

    /**
     * RestClient with no fixed base URL — {@link GitLabPrSourceClient} resolves apiBaseUrl per call
     * (per-repo overrides). Carries two converters: Jackson for the JSON endpoints (MR metadata,
     * approvals, project, group members) and String for the raw-file endpoint
     * ({@code /repository/files/:path/raw}), which serves {@code text/plain} content.
     */
    @Bean
    public RestClient gitLabRestClient(ObjectMapper objectMapper) {
        return RestClient.builder()
                .messageConverters(ImmutableList.of(
                        new MappingJackson2HttpMessageConverter(objectMapper),
                        new StringHttpMessageConverter(StandardCharsets.UTF_8)))
                .build();
    }

    @Bean
    public GitLabGroupMemberCache gitLabGroupMemberCache(PrTrackingProps props) {
        return new GitLabGroupMemberCache(
                Objects.requireNonNull(props.slaDiscovery().cache(), "slaDiscovery.cache must not be null"));
    }

    @Bean
    public PrSourceClient gitLabPrSourceClient(
            RestClient gitLabRestClient, PrTrackingProps props, GitLabGroupMemberCache gitLabGroupMemberCache) {
        return new GitLabPrSourceClient(gitLabRestClient, props, gitLabGroupMemberCache);
    }
}
