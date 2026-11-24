package com.coreeng.supportbot.analysis.llm;

import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;

import com.coreeng.supportbot.config.ConditionalOnLlmEnabled;
import com.coreeng.supportbot.config.LlmProps;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
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
 *
 * <p>Each real client declares that it accepts a JSON schema for its output, because the clients do
 * not declare it themselves; a caller that asks for structured output checks for it.
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
                .supportedCapabilities(RESPONSE_FORMAT_JSON_SCHEMA)
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
                .timeout(llm.proxy().timeout())
                .supportedCapabilities(RESPONSE_FORMAT_JSON_SCHEMA);
        if (httpClientBuilder != null) {
            builder.httpClientBuilder(httpClientBuilder);
        }
        return builder.build();
    }

    @Bean
    @ConditionalOnProperty(name = "llm.provider", havingValue = "anthropic")
    public ChatModel anthropicChatModel(LlmProps llm) {
        return anthropicChatModel(llm, null);
    }

    ChatModel anthropicChatModel(LlmProps llm, @Nullable HttpClientBuilder httpClientBuilder) {
        LlmProps.Anthropic anthropic = llm.anthropic();
        log.info(
                "Configuring Anthropic model: baseUrl={}, model={}, timeout={}, maxTokens={}",
                anthropic.baseUrl().isEmpty() ? "<default>" : anthropic.baseUrl(),
                llm.modelName(),
                anthropic.timeout(),
                anthropic.maxTokens());

        var builder = AnthropicChatModel.builder()
                .apiKey(anthropic.apiKey())
                .modelName(llm.modelName())
                .maxTokens(anthropic.maxTokens())
                .timeout(anthropic.timeout())
                .supportedCapabilities(RESPONSE_FORMAT_JSON_SCHEMA);
        if (!anthropic.baseUrl().isEmpty()) {
            builder.baseUrl(anthropic.baseUrl());
        }
        if (httpClientBuilder != null) {
            builder.httpClientBuilder(httpClientBuilder);
        }
        return builder.build();
    }
}
