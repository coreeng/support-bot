package com.coreeng.supportbot.analysis.llm;

import com.coreeng.supportbot.config.ConditionalOnLlmEnabled;
import com.coreeng.supportbot.config.LlmProps;
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

/**
 * Builds the one {@link ChatModel} named by {@code llm.provider}. With the provider set to
 * {@code none} this configuration is not registered at all and no client exists.
 */
@Configuration
@ConditionalOnLlmEnabled
@Slf4j
public class LlmConfig {

    @Bean
    @ConditionalOnProperty(name = "llm.provider", havingValue = "vertex")
    public ChatModel vertexChatModel(LlmProps llm) {
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
     * <p>Local-only: its output lands in {@code analysis} and {@code summary_snapshot} like real
     * model output. {@link LlmProps.Stub#validate()} refuses to start unless
     * {@code llm.stub.acknowledge-synthetic-data=true} is also set, so this bean only exists once an
     * operator has opted in twice.
     */
    @Bean
    @ConditionalOnProperty(name = "llm.provider", havingValue = "stub")
    public ChatModel stubChatModel() {
        log.warn("Using the STUB LLM provider: responses are canned and describe no real data;"
                + " classifications and summaries written from here are synthetic");
        return new StubChatModel();
    }

    @Bean
    @ConditionalOnProperty(name = "llm.provider", havingValue = "proxy")
    public ChatModel proxyChatModel(LlmProps llm) {
        return proxyChatModel(llm, null);
    }

    // Test seam: the contract test injects a capturing HttpClientBuilder to assert the outgoing
    // request without a server. Null means the client's default HTTP transport.
    ChatModel proxyChatModel(LlmProps llm, @Nullable HttpClientBuilder httpClientBuilder) {
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
