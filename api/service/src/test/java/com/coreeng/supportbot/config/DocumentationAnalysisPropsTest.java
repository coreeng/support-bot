package com.coreeng.supportbot.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.util.unit.DataSize;

class DocumentationAnalysisPropsTest {

    @Test
    void isOffByDefaultWithTheDesignDefaults() {
        DocumentationAnalysisProps props = bind(new HashMap<>());

        assertThat(props.enabled()).isFalse();
        assertThat(props.maxInputTokens()).isEqualTo(150_000);
        assertThat(props.maxTicketsPerDay()).isEqualTo(500);
        assertThat(props.failureRetryDelay()).isEqualTo(Duration.ofHours(24));
        assertThat(props.maxAttempts()).isEqualTo(3);
        assertThat(props.runRetryDelay()).isEqualTo(Duration.ofMinutes(15));
        assertThat(props.source().type()).isEqualTo("github");
        DocumentationAnalysisProps.GitHub github = props.source().github();
        assertThat(github.repository()).isEmpty();
        assertThat(github.branch()).isEqualTo("main");
        assertThat(github.path()).isEqualTo("docs");
        assertThat(github.fileExtensions()).containsExactly(".md", ".mdx");
        assertThat(github.copyLocationPath()).isEqualTo(Path.of("/tmp/documentation-analysis"));
        assertThat(github.maxCopyAge()).isEqualTo(Duration.ofHours(24));
        assertThat(github.maxCopySize()).isEqualTo(DataSize.ofMegabytes(50));
        assertThat(github.maxFiles()).isEqualTo(5000);
        assertThat(github.maxAreasPerTicket()).isEqualTo(2);
    }

    @Test
    void validatesNothingWhileOff() {
        Map<String, Object> values = new HashMap<>();
        values.put("documentation-analysis.source.type", "confluence");
        values.put("documentation-analysis.max-input-tokens", "0");
        values.put("documentation-analysis.source.github.repository", "not-a-repository");

        assertThat(bind(values).enabled()).isFalse();
    }

    @Test
    void failsOnAValueThatCannotBeReadEvenWhileOff() {
        Map<String, Object> values = new HashMap<>();
        values.put("documentation-analysis.max-input-tokens", "abc");

        assertThatThrownBy(() -> bind(values)).hasStackTraceContaining("max-input-tokens");
    }

    @Test
    void bindsAnEnabledGitHubSource() {
        Map<String, Object> values = enabledGitHub();
        values.put("documentation-analysis.source.github.path", "/site/docs/");
        values.put("documentation-analysis.source.github.file-extensions", ".MD, .mdx");

        DocumentationAnalysisProps props = bind(values);

        assertThat(props.enabled()).isTrue();
        assertThat(props.source().selectedType()).isEqualTo(DocumentationSourceType.GITHUB);
        assertThat(props.source().github().repository()).isEqualTo("example-org/docs");
        assertThat(props.source().github().path()).isEqualTo("site/docs");
        assertThat(props.source().github().fileExtensions()).containsExactly(".md", ".mdx");
    }

    @ParameterizedTest
    @ValueSource(strings = {"github", "GITHUB", " GitHub "})
    void selectsTheSourceCaseInsensitively(String type) {
        Map<String, Object> values = enabledGitHub();
        values.put("documentation-analysis.source.type", type);

        assertThat(bind(values).source().selectedType()).isEqualTo(DocumentationSourceType.GITHUB);
    }

    @ParameterizedTest
    @ValueSource(strings = {"confluence", ""})
    void failsWhenEnabledWithAnUnknownSource(String type) {
        Map<String, Object> values = enabledGitHub();
        values.put("documentation-analysis.source.type", type);

        assertThatThrownBy(() -> bind(values))
                .hasStackTraceContaining(
                        "documentation-analysis.source.type must be one of github but was '" + type + "'");
    }

    @Test
    void failsWhenEnabledWithTheGitHubSourceAndNoRepository() {
        Map<String, Object> values = enabledGitHub();
        values.put("documentation-analysis.source.github.repository", " ");

        assertThatThrownBy(() -> bind(values))
                .hasStackTraceContaining("documentation-analysis.source.github.repository is required"
                        + " when documentation-analysis.source.type=github");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", ".", "/", "./", "//"})
    void readsABlankOrDotPathAsTheRepositoryRoot(String path) {
        Map<String, Object> values = enabledGitHub();
        values.put("documentation-analysis.source.github.path", path);

        assertThat(bind(values).source().github().path()).isEmpty();
    }

    @Test
    void readsABareNumberInTheUnitOfItsDefault() {
        Map<String, Object> values = enabledGitHub();
        values.put("documentation-analysis.failure-retry-delay", "24");
        values.put("documentation-analysis.run-retry-delay", "15");
        values.put("documentation-analysis.source.github.max-copy-age", "12");
        values.put("documentation-analysis.source.github.max-copy-size", "50");

        DocumentationAnalysisProps props = bind(values);

        assertThat(props.failureRetryDelay()).isEqualTo(Duration.ofHours(24));
        assertThat(props.runRetryDelay()).isEqualTo(Duration.ofMinutes(15));
        assertThat(props.source().github().maxCopyAge()).isEqualTo(Duration.ofHours(12));
        assertThat(props.source().github().maxCopySize()).isEqualTo(DataSize.ofMegabytes(50));
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "source.github.repository | example-org     | source.github.repository must be in owner/name format",
                "source.github.repository | a/b/c           | source.github.repository must be in owner/name format",
                "source.github.branch     | ' '             | source.github.branch is required",
                "source.github.path       | docs/../secrets | source.github.path must be a relative directory",
                "source.github.path       | ./docs          | source.github.path must be a relative directory",
                "source.github.file-extensions | md         | source.github.file-extensions must each start with '.'",
                "source.github.copy-location | tmp/copy     | source.github.copy-location must be an absolute path",
                "source.github.copy-location | /            | source.github.copy-location must be an absolute path",
                "source.github.copy-location | /tmp/..      | source.github.copy-location must be an absolute path",
                "source.github.copy-location | ' '          | source.github.copy-location must be an absolute path",
                "source.github.max-copy-age | 0s            | source.github.max-copy-age must be positive",
                "source.github.max-copy-size | 0B           | source.github.max-copy-size must be positive",
                "source.github.max-files  | 0               | source.github.max-files must be positive",
                "source.github.max-areas-per-ticket | 0     | source.github.max-areas-per-ticket must be positive",
                "max-input-tokens         | 0               | max-input-tokens must be positive",
                "max-tickets-per-day      | -1              | max-tickets-per-day must be positive",
                "failure-retry-delay      | 0s              | failure-retry-delay must be positive",
                "max-attempts             | 0               | max-attempts must be positive",
                "run-retry-delay          | -1m             | run-retry-delay must be positive"
            })
    void failsWhenEnabledWithAnInvalidSetting(String setting, String value, String message) {
        Map<String, Object> values = enabledGitHub();
        values.put("documentation-analysis." + setting, value);

        assertThatThrownBy(() -> bind(values)).hasStackTraceContaining("documentation-analysis." + message);
    }

    private static Map<String, Object> enabledGitHub() {
        Map<String, Object> values = new HashMap<>();
        values.put("documentation-analysis.enabled", "true");
        values.put("documentation-analysis.source.type", "github");
        values.put("documentation-analysis.source.github.repository", "example-org/docs");
        return values;
    }

    private static DocumentationAnalysisProps bind(Map<String, Object> values) {
        return new Binder(new MapConfigurationPropertySource(values))
                .bindOrCreate("documentation-analysis", DocumentationAnalysisProps.class);
    }
}
