package com.coreeng.supportbot.analysis.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.coreeng.supportbot.config.LlmProps;
import com.coreeng.supportbot.config.LlmProvider;
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
    void doesNotCreateChatModelByDefault() {
        // llm.provider unset: the feature is off and no client of any kind is built.
        contextRunner.withPropertyValues(vertexProperties()).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(ChatModel.class);
        });
    }

    @Test
    void doesNotCreateChatModelWhenProviderIsNone() {
        contextRunner
                .withPropertyValues(vertexProperties())
                .withPropertyValues("llm.provider=none")
                .run(context -> assertThat(context).doesNotHaveBean(ChatModel.class));
    }

    @Test
    void treatsBlankProviderAsNone() {
        // LLM_PROVIDER set to an empty string (a common shape in env files) is the same as unset.
        contextRunner
                .withPropertyValues(vertexProperties())
                .withPropertyValues("llm.provider=")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(ChatModel.class);
                    assertThat(context.getBean(LlmProps.class).provider()).isEqualTo(LlmProvider.NONE);
                });
    }

    @Test
    void startsCleanlyWithNoProviderDespiteInvalidLlmConfig() {
        // Rejected when a provider is selected, but with none selected this config is never used and
        // must not block startup.
        contextRunner
                .withPropertyValues("llm.provider=none", "llm.model-name=", "llm.request-delay=-1s")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(ChatModel.class);
                });
    }

    @Test
    void createsOnlyVertexModelWhenVertexSelected() {
        contextRunner
                .withPropertyValues(vertexProperties())
                .withPropertyValues("llm.provider=vertex")
                .run(context -> {
                    assertThat(context).hasSingleBean(ChatModel.class);
                    assertThat(context.getBean(ChatModel.class)).isInstanceOf(VertexAiGeminiChatModel.class);
                });
    }

    @Test
    void providerNameIsCaseInsensitive() {
        // LLM_PROVIDER=VERTEX from a shell must select the same bean as the lower-case yaml value.
        contextRunner
                .withPropertyValues(vertexProperties())
                .withPropertyValues("llm.provider=VERTEX")
                .run(context ->
                        assertThat(context.getBean(ChatModel.class)).isInstanceOf(VertexAiGeminiChatModel.class));
    }

    @Test
    void createsOnlyProxyModelWhenProxySelected() {
        // Vertex settings deliberately absent: proxy mode must not need them.
        contextRunner
                .withPropertyValues(proxyProperties())
                .withPropertyValues("llm.provider=proxy")
                .run(context -> {
                    assertThat(context).hasSingleBean(ChatModel.class);
                    assertThat(context.getBean(ChatModel.class)).isInstanceOf(GoogleAiGeminiChatModel.class);
                });
    }

    @Test
    void failsStartupWhenProxyModeMissingBaseUrl() {
        contextRunner
                .withPropertyValues(
                        "llm.provider=proxy",
                        "llm.model-name=gemini-2.5-flash",
                        "llm.proxy.auth.basic-auth-token=dXNlcjpwYXNz")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("llm.proxy.base-url");
                });
    }

    @Test
    void failsStartupWhenVertexModeMissingProjectId() {
        contextRunner
                .withPropertyValues(
                        "llm.provider=vertex", "llm.model-name=gemini-2.5-flash", "llm.vertex.location=europe-west2")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("llm.vertex.project-id");
                });
    }

    @Test
    void failsStartupOnUnknownProvider() {
        // A typo must not silently mean "off": the choices are spelled out instead.
        contextRunner
                .withPropertyValues(vertexProperties())
                .withPropertyValues("llm.provider=vertexai")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .hasMessageContaining("llm.provider must be one of none, vertex, proxy, stub")
                            .hasMessageContaining("vertexai");
                });
    }

    @Test
    void createsOnlyStubModelWhenStubSelected() {
        // Vertex and proxy settings deliberately absent: the stub must need no credentials at all —
        // that is the whole point of it.
        contextRunner.withPropertyValues(stubProperties()).run(context -> {
            assertThat(context).hasSingleBean(ChatModel.class);
            assertThat(context.getBean(ChatModel.class)).isInstanceOf(StubChatModel.class);
        });
    }

    @Test
    void failsStartupWhenStubSelectedWithoutSyntheticDataAcknowledgement() {
        // The provider on its own must not start: a config copied from a laptop into a shared
        // environment would otherwise write synthetic rows there.
        contextRunner
                .withPropertyValues("llm.provider=stub", "llm.model-name=stub-local")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .hasMessageContaining("llm.stub.acknowledge-synthetic-data=true")
                            .hasMessageContaining("synthetic rows into analysis and summary_snapshot")
                            .hasMessageContaining("never be enabled against a shared database");
                });
    }

    private static String[] stubProperties() {
        return new String[] {
            "llm.provider=stub", "llm.stub.acknowledge-synthetic-data=true", "llm.model-name=stub-local"
        };
    }

    private static String[] vertexProperties() {
        return new String[] {
            "llm.model-name=gemini-2.5-flash", "llm.vertex.project-id=test-project", "llm.vertex.location=europe-west2"
        };
    }

    private static String[] proxyProperties() {
        return new String[] {
            "llm.model-name=gemini-2.5-flash",
            "llm.proxy.base-url=http://localhost:9999/platform/google-vertex/proxy/v1beta",
            "llm.proxy.auth.basic-auth-token=dXNlcjpwYXNz"
        };
    }

    @Configuration
    @EnableConfigurationProperties(LlmProps.class)
    static class TestConfig {}
}
