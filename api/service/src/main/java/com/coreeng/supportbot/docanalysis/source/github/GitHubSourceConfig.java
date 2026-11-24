package com.coreeng.supportbot.docanalysis.source.github;

import com.coreeng.supportbot.analysis.AnalysisService;
import com.coreeng.supportbot.config.ConditionalOnLlmEnabled;
import com.coreeng.supportbot.config.DocumentationAnalysisProps;
import com.coreeng.supportbot.docanalysis.DocumentationSource;
import com.coreeng.supportbot.github.GitHubClient;
import dev.langchain4j.model.chat.ChatModel;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnLlmEnabled
@ConditionalOnProperty(name = "documentation-analysis.enabled", havingValue = "true")
@ConditionalOnProperty(name = "documentation-analysis.source.type", havingValue = "github", matchIfMissing = true)
class GitHubSourceConfig {

    @Bean
    DocumentationCopy documentationCopy(GitHubClient gitHubClient, DocumentationAnalysisProps props, Clock clock) {
        return new DocumentationCopy(gitHubClient, props.source().github(), clock);
    }

    @Bean
    DocumentationSource gitHubDocumentationSource(
            DocumentationCopy documentationCopy,
            DocumentationAnalysisProps props,
            ChatModel chatModel,
            AnalysisService analysisService) {
        return new GitHubDocumentationSource(
                documentationCopy, props.source().github(), props.maxInputTokens(), chatModel, analysisService);
    }
}
