package com.coreeng.supportbot.analysis.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.coreeng.supportbot.config.AnalysisProps;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.vertexai.gemini.VertexAiGeminiChatModel;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class LlmConfigTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(TestConfig.class, LlmConfig.class);

    @Test
    void doesNotCreateChatModelWhenPromptDisabled() {
        contextRunner
                .withPropertyValues(vertexProperties())
                .withPropertyValues("analysis.prompt.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(ChatModel.class));
    }

    @Test
    void startsCleanlyWhenPromptDisabledDespiteInvalidLlmConfig() {
        // No model name and neither provider enabled: rejected when the feature is on, but with
        // the feature off this config is never used and must not block startup.
        contextRunner
                .withPropertyValues("analysis.prompt.enabled=false", "analysis.llm.vertex.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(ChatModel.class);
                });
    }

    @Test
    void defaultsToVertexModelWhenProxyNotEnabled() {
        contextRunner
                .withPropertyValues(vertexProperties())
                .withPropertyValues("analysis.prompt.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(ChatModel.class);
                    assertThat(context.getBean(ChatModel.class)).isInstanceOf(VertexAiGeminiChatModel.class);
                });
    }

    @Test
    void createsOnlyProxyModelWhenProxyEnabled() {
        // Vertex settings deliberately absent: proxy mode must not need them.
        contextRunner
                .withPropertyValues(proxyProperties())
                .withPropertyValues("analysis.prompt.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(ChatModel.class);
                    assertThat(context.getBean(ChatModel.class)).isInstanceOf(GoogleAiGeminiChatModel.class);
                });
    }

    @Test
    void failsStartupWhenProxyModeMissingBaseUrl() {
        contextRunner
                .withPropertyValues(
                        "analysis.prompt.enabled=true",
                        "analysis.llm.vertex.enabled=false",
                        "analysis.llm.proxy.enabled=true",
                        "analysis.llm.model-name=gemini-2.5-flash",
                        "analysis.llm.proxy.auth.basic-auth-token=dXNlcjpwYXNz")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .hasMessageContaining("analysis.llm.proxy.base-url");
                });
    }

    @Test
    void failsStartupWhenVertexModeMissingProjectId() {
        contextRunner
                .withPropertyValues(
                        "analysis.prompt.enabled=true",
                        "analysis.llm.model-name=gemini-2.5-flash",
                        "analysis.llm.vertex.location=europe-west2")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .hasMessageContaining("analysis.llm.vertex.project-id");
                });
    }

    @Test
    void failsStartupWhenBothProvidersEnabled() {
        contextRunner
                .withPropertyValues(vertexProperties())
                .withPropertyValues("analysis.prompt.enabled=true", "analysis.llm.proxy.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .hasMessageContaining("exactly one of analysis.llm.vertex.enabled,");
                });
    }

    @Test
    void createsOnlyGoogleAiModelWhenGoogleAiEnabled() {
        // Vertex and proxy settings deliberately absent: the local-dev mode must need neither GCP
        // project nor proxy credentials — that is the whole point of it.
        contextRunner
                .withPropertyValues(googleAiProperties())
                .withPropertyValues("analysis.prompt.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(ChatModel.class);
                    assertThat(context.getBean(ChatModel.class)).isInstanceOf(GoogleAiGeminiChatModel.class);
                });
    }

    @Test
    void failsStartupWhenGoogleAiModeMissingApiKey() {
        contextRunner
                .withPropertyValues(
                        "analysis.prompt.enabled=true",
                        "analysis.llm.vertex.enabled=false",
                        "analysis.llm.google-ai.enabled=true",
                        "analysis.llm.model-name=gemini-2.5-flash")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .hasMessageContaining("analysis.llm.google-ai.api-key");
                });
    }

    @Test
    void failsStartupWhenGoogleAiAndProxyBothEnabled() {
        contextRunner
                .withPropertyValues(googleAiProperties())
                .withPropertyValues(
                        "analysis.prompt.enabled=true",
                        "analysis.llm.proxy.enabled=true",
                        "analysis.llm.proxy.base-url=https://llm-proxy.example.test/proxy/v1beta",
                        "analysis.llm.proxy.auth.basic-auth-token=dXNlcjpwYXNz")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .hasMessageContaining("exactly one of analysis.llm.vertex.enabled,");
                });
    }

    @Test
    void failsStartupWhenNeitherProviderEnabled() {
        contextRunner
                .withPropertyValues(vertexProperties())
                .withPropertyValues("analysis.prompt.enabled=true", "analysis.llm.vertex.enabled=false")
                .run(context -> assertThat(context).hasFailed());
    }

    private static String[] googleAiProperties() {
        return new String[] {
            "analysis.llm.vertex.enabled=false",
            "analysis.llm.google-ai.enabled=true",
            "analysis.llm.model-name=gemini-2.5-flash",
            "analysis.llm.google-ai.api-key=AIzaSyTestKeyNotReal"
        };
    }

    private static String[] vertexProperties() {
        return new String[] {
            "analysis.llm.model-name=gemini-2.5-flash",
            "analysis.llm.vertex.project-id=test-project",
            "analysis.llm.vertex.location=europe-west2"
        };
    }

    private static String[] proxyProperties() {
        return new String[] {
            "analysis.llm.vertex.enabled=false",
            "analysis.llm.proxy.enabled=true",
            "analysis.llm.model-name=gemini-2.5-flash",
            "analysis.llm.proxy.base-url=http://localhost:9999/platform/google-vertex/proxy/v1beta",
            "analysis.llm.proxy.auth.basic-auth-token=dXNlcjpwYXNz"
        };
    }

    @Configuration
    @EnableConfigurationProperties(AnalysisProps.class)
    static class TestConfig {}
}
