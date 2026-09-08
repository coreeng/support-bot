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
    void createsOnlyStubModelWhenStubEnabled() {
        // Vertex and proxy settings deliberately absent: the stub must need no credentials at all —
        // that is the whole point of it.
        contextRunner
                .withPropertyValues(stubProperties())
                .withPropertyValues("analysis.prompt.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(ChatModel.class);
                    assertThat(context.getBean(ChatModel.class)).isInstanceOf(StubChatModel.class);
                });
    }

    @Test
    void failsStartupWhenStubEnabledWithoutSyntheticDataAcknowledgement() {
        // The provider flag on its own must not start: a config copied from a laptop into a shared
        // environment would otherwise write synthetic rows there.
        contextRunner
                .withPropertyValues(
                        "analysis.prompt.enabled=true",
                        "analysis.llm.vertex.enabled=false",
                        "analysis.llm.stub.enabled=true",
                        "analysis.llm.model-name=stub-local")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .hasMessageContaining("analysis.llm.stub.acknowledge-synthetic-data=true")
                            .hasMessageContaining("synthetic rows into analysis and summary_snapshot")
                            .hasMessageContaining("never be enabled against a shared database");
                });
    }

    @Test
    void failsStartupWhenStubAndProxyBothEnabled() {
        contextRunner
                .withPropertyValues(stubProperties())
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

    private static String[] stubProperties() {
        return new String[] {
            "analysis.llm.vertex.enabled=false",
            "analysis.llm.stub.enabled=true",
            "analysis.llm.stub.acknowledge-synthetic-data=true",
            "analysis.llm.model-name=stub-local"
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
