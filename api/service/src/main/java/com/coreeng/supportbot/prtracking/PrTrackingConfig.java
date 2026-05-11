package com.coreeng.supportbot.prtracking;

import com.coreeng.supportbot.config.PrTrackingProps;
import com.coreeng.supportbot.prtracking.source.Provider;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "pr-review-tracking.enabled", havingValue = "true")
public class PrTrackingConfig {

    @Bean
    public PrUrlResolver prUrlResolver(PrTrackingProps props) {
        return new PrUrlResolver(props);
    }

    @Bean
    public GitHubPrUrlParser gitHubPrUrlParser(PrTrackingProps props) {
        Set<String> repoNames = props.repositories().stream()
                .filter(r -> r.provider() == Provider.GITHUB)
                .map(PrTrackingProps.Repository::name)
                .collect(Collectors.toUnmodifiableSet());
        return new GitHubPrUrlParser(repoNames);
    }

    @Bean
    public GitLabMrUrlParser gitLabMrUrlParser(PrTrackingProps props, PrUrlResolver resolver) {
        Set<String> repoNames = props.repositories().stream()
                .filter(r -> r.provider() == Provider.GITLAB)
                .map(PrTrackingProps.Repository::name)
                .collect(Collectors.toUnmodifiableSet());
        return new GitLabMrUrlParser(resolver.gitLabHosts(), repoNames);
    }

    @Bean
    public PrUrlDispatcher prUrlDispatcher(GitHubPrUrlParser gitHubPrUrlParser, GitLabMrUrlParser gitLabMrUrlParser) {
        return new PrUrlDispatcher(gitHubPrUrlParser, gitLabMrUrlParser);
    }
}
