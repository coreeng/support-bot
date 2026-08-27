package com.coreeng.supportbot.analysis.llm;

import com.coreeng.supportbot.config.AnalysisProps;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.vertexai.gemini.VertexAiGeminiChatModel;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "analysis.prompt.enabled", havingValue = "true")
@Slf4j
public class LlmConfig {

    @Bean
    @ConditionalOnProperty(
            prefix = "analysis.llm.vertex",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    public ChatModel vertexChatModel(AnalysisProps analysisProps) {
        AnalysisProps.Llm llm = analysisProps.llm();
        log.info(
                "Configuring Vertex AI model: project={}, location={}, model={}",
                llm.vertex().projectId(),
                llm.vertex().location(),
                llm.modelName());

        return VertexAiGeminiChatModel.builder()
                .project(llm.vertex().projectId())
                .location(llm.vertex().location())
                .modelName(llm.modelName())
                .build();
    }

    /**
     * Local development and demo provider: canned deterministic responses, no network, no
     * credentials, no spend. See {@link StubChatModel} for what it returns and how it tells the two
     * callers apart.
     *
     * <p>May be dropped before merge — see {@code docs/plans/support-summary.md}.
     */
    @Bean
    @ConditionalOnProperty(prefix = "analysis.llm.stub", name = "enabled", havingValue = "true")
    public ChatModel stubChatModel() {
        log.warn("Using the STUB LLM provider: responses are canned and describe no real data");
        return new StubChatModel();
    }

    @Bean
    @ConditionalOnProperty(prefix = "analysis.llm.proxy", name = "enabled", havingValue = "true")
    public ChatModel proxyChatModel(AnalysisProps analysisProps) {
        return proxyChatModel(analysisProps, null);
    }

    // Test seam: the contract test injects a capturing HttpClientBuilder to assert the outgoing
    // request without a server. Null means the client's default HTTP transport.
    ChatModel proxyChatModel(AnalysisProps analysisProps, @Nullable HttpClientBuilder httpClientBuilder) {
        AnalysisProps.Llm llm = analysisProps.llm();
        log.info(
                "Configuring proxied model: baseUrl={}, model={}, timeout={}",
                llm.proxy().baseUrl(),
                llm.modelName(),
                llm.proxy().timeout());

        // No apiKey: the proxy authenticates via the Basic header, and the client
        // only sends x-goog-api-key when an apiKey is set.
        var builder = GoogleAiGeminiChatModel.builder()
                .baseUrl(llm.proxy().baseUrl())
                .modelName(llm.modelName())
                .customHeaders(
                        Map.of("Authorization", "Basic " + llm.proxy().auth().basicAuthToken()))
                .timeout(llm.proxy().timeout());
        if (httpClientBuilder != null) {
            builder.httpClientBuilder(httpClientBuilder);
        }
        return builder.build();
    }
}
