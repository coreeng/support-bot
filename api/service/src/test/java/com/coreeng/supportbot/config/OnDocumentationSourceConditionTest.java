package com.coreeng.supportbot.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class OnDocumentationSourceConditionTest {

    private static final String REPOSITORY = "documentation-analysis.source.github.repository=example-org/docs";

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(TestConfig.class, FeatureConfig.class);

    @Test
    void buildsNothingByDefault() {
        contextRunner.withPropertyValues("llm.provider=vertex").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(AnalysisMarker.class);
            assertThat(context).doesNotHaveBean(GitHubSourceMarker.class);
        });
    }

    @Test
    void buildsTheAnalysisAndTheSelectedSourceWhenEnabledWithAnLlmProvider() {
        contextRunner
                .withPropertyValues(
                        "llm.provider=vertex",
                        "documentation-analysis.enabled=true",
                        "documentation-analysis.source.type=GitHub",
                        REPOSITORY)
                .run(context -> {
                    assertThat(context).hasSingleBean(AnalysisMarker.class);
                    assertThat(context).hasSingleBean(GitHubSourceMarker.class);
                });
    }

    @Test
    void selectsTheGitHubSourceWhenNoTypeIsSet() {
        contextRunner
                .withPropertyValues("llm.provider=vertex", "documentation-analysis.enabled=true", REPOSITORY)
                .run(context -> assertThat(context).hasSingleBean(GitHubSourceMarker.class));
    }

    @Test
    void buildsNothingWithoutAnLlmProvider() {
        contextRunner
                .withPropertyValues("llm.provider=none", "documentation-analysis.enabled=true", REPOSITORY)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(AnalysisMarker.class);
                    assertThat(context).doesNotHaveBean(GitHubSourceMarker.class);
                });
    }

    @Test
    void doesNotStartWhenEnabledWithAnUnknownSource() {
        contextRunner
                .withPropertyValues(
                        "llm.provider=vertex",
                        "documentation-analysis.enabled=true",
                        "documentation-analysis.source.type=confluence")
                .run(context -> assertThat(context)
                        .getFailure()
                        .hasStackTraceContaining(
                                "documentation-analysis.source.type must be one of github but was 'confluence'"));
    }

    @Test
    void doesNotStartWhenEnabledWithTheGitHubSourceAndNoRepository() {
        contextRunner
                .withPropertyValues("llm.provider=vertex", "documentation-analysis.enabled=true")
                .run(context -> assertThat(context)
                        .getFailure()
                        .hasStackTraceContaining("documentation-analysis.source.github.repository is required"));
    }

    @Test
    void doesNotStartWhenEnabledWithoutAnLlmProviderAndNoRepository() {
        contextRunner
                .withPropertyValues("llm.provider=none", "documentation-analysis.enabled=true")
                .run(context -> assertThat(context)
                        .getFailure()
                        .hasStackTraceContaining("documentation-analysis.source.github.repository is required"));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(DocumentationAnalysisProps.class)
    static class TestConfig {}

    @Configuration(proxyBeanMethods = false)
    static class FeatureConfig {
        @Bean
        @ConditionalOnDocumentationAnalysis
        AnalysisMarker analysisMarker() {
            return new AnalysisMarker();
        }

        @Bean
        @ConditionalOnDocumentationSource(DocumentationSourceType.GITHUB)
        GitHubSourceMarker gitHubSourceMarker() {
            return new GitHubSourceMarker();
        }
    }

    static final class AnalysisMarker {}

    static final class GitHubSourceMarker {}
}
