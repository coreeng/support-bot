package com.coreeng.supportbot.prtracking;

import com.coreeng.supportbot.config.PrTrackingProps;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "pr-review-tracking.enabled", havingValue = "true")
public class PrTrackingConfig {

    @Bean
    public GitHubPrUrlParser gitHubPrUrlParser(PrTrackingProps props) {
        Set<String> repoNames = props.repositories().stream()
                .map(PrTrackingProps.Repository::name)
                .collect(Collectors.toUnmodifiableSet());
        return new GitHubPrUrlParser(repoNames);
    }
}
