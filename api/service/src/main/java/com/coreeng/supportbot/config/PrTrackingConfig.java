package com.coreeng.supportbot.config;

import com.coreeng.supportbot.prtracking.GitHubPrUrlParser;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "pr-identification.enabled", havingValue = "true")
public class PrTrackingConfig {

    @Bean
    public GitHubPrUrlParser gitHubPrUrlParser(PrTrackingProps props) {
        Set<String> repoNames = props.repositories().stream()
                .map(PrTrackingRepositoryProps::name)
                .collect(Collectors.toUnmodifiableSet());
        return new GitHubPrUrlParser(repoNames);
    }
}
