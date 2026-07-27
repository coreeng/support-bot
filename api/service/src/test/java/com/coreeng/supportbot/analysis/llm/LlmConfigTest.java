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
    void defaultsToVertexModelWhenProviderOmitted() {
        contextRunner
                .withPropertyValues(vertexProperties())
                .withPropertyValues("analysis.prompt.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(ChatModel.class);
                    assertThat(context.getBean(ChatModel.class)).isInstanceOf(VertexAiGeminiChatModel.class);
                });
    }

    @Test
    void createsOnlyGatewayModelWhenProviderGateway() {
        // Vertex settings deliberately absent: gateway mode must not need them.
        contextRunner
                .withPropertyValues(gatewayProperties())
                .withPropertyValues("analysis.prompt.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(ChatModel.class);
                    assertThat(context.getBean(ChatModel.class)).isInstanceOf(GoogleAiGeminiChatModel.class);
                });
    }

    @Test
    void failsStartupWhenGatewayModeMissingBaseUrl() {
        contextRunner
                .withPropertyValues(
                        "analysis.prompt.enabled=true",
                        "analysis.llm.provider=gateway",
                        "analysis.llm.model-name=gemini-2.5-flash",
                        "analysis.llm.gateway.basic-auth-token=dXNlcjpwYXNz")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .hasMessageContaining("analysis.llm.gateway.base-url");
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
    void failsStartupOnUnknownProvider() {
        contextRunner
                .withPropertyValues(vertexProperties())
                .withPropertyValues("analysis.prompt.enabled=true", "analysis.llm.provider=azure")
                .run(context -> assertThat(context).hasFailed());
    }

    private static String[] vertexProperties() {
        return new String[] {
            "analysis.llm.model-name=gemini-2.5-flash",
            "analysis.llm.vertex.project-id=test-project",
            "analysis.llm.vertex.location=europe-west2"
        };
    }

    private static String[] gatewayProperties() {
        return new String[] {
            "analysis.llm.provider=gateway",
            "analysis.llm.model-name=gemini-2.5-flash",
            "analysis.llm.gateway.base-url=http://localhost:9999/platform/google-vertex/proxy/v1beta",
            "analysis.llm.gateway.basic-auth-token=dXNlcjpwYXNz"
        };
    }

    @Configuration
    @EnableConfigurationProperties(AnalysisProps.class)
    static class TestConfig {}
}
