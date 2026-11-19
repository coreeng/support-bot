package com.coreeng.supportbot.analysis.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.coreeng.supportbot.config.LlmProps;
import com.coreeng.supportbot.config.LlmProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpMethod;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.model.chat.ChatModel;
import java.time.Duration;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

/**
 * Pins the wire contract with the Claude API at langchain4j's {@link HttpClient} boundary: the
 * request the client would put on the wire (URL, API key header, Messages API body) and how our
 * timeout setting maps onto the transport. No server and no real key involved.
 */
class AnthropicChatModelContractTest {

    private static final String API_KEY = "test-anthropic-key";
    private static final String MODEL_NAME = "claude-opus-5-5";
    private static final String CUSTOM_BASE_URL = "https://llm-gateway.example.test/anthropic/v1";

    private static final String MESSAGES_RESPONSE = """
            {
              "id": "msg_test",
              "type": "message",
              "role": "assistant",
              "model": "claude-opus-5-5",
              "content": [{"type": "text", "text": "Primary Driver: Knowledge Gap"}],
              "stop_reason": "end_turn",
              "stop_sequence": null,
              "usage": {"input_tokens": 1, "output_tokens": 1}
            }
            """;

    private final HttpClientBuilder httpClientBuilder = mock(HttpClientBuilder.class, RETURNS_SELF);
    private final HttpClient httpClient = mock(HttpClient.class);

    @Test
    void sendsMessagesRequestToTheDefaultEndpointWithApiKeyModelAndMaxTokens() throws Exception {
        ChatModel model = anthropicModel("", Duration.ofSeconds(5));

        String response = model.chat("hello claude");

        assertThat(response).contains("Primary Driver: Knowledge Gap");
        HttpRequest request = capturedRequest();
        assertThat(request.method()).isEqualTo(HttpMethod.POST);
        assertThat(request.url()).isEqualTo("https://api.anthropic.com/v1/messages");
        assertThat(request.headers().get("x-api-key")).containsExactly(API_KEY);
        assertThat(request.headers().keySet()).noneSatisfy(header -> assertThat(header.toLowerCase(Locale.ROOT))
                .isEqualTo("authorization"));

        JsonNode body = new ObjectMapper().readTree(request.body());
        assertThat(body.at("/model").asText()).isEqualTo(MODEL_NAME);
        assertThat(body.at("/max_tokens").asInt()).isEqualTo(16000);
        assertThat(body.at("/messages/0/role").asText()).isEqualTo("user");
        assertThat(body.toString()).contains("hello claude");
        assertThat(body.has("thinking")).isFalse();
    }

    @ParameterizedTest(name = "llm.anthropic.base-url={0}")
    @ValueSource(strings = {CUSTOM_BASE_URL, CUSTOM_BASE_URL + "/"})
    void sendsToTheConfiguredBaseUrl(String baseUrl) {
        anthropicModel(baseUrl, Duration.ofSeconds(5)).chat("hello claude");

        assertThat(capturedRequest().url()).isEqualTo(CUSTOM_BASE_URL + "/messages");
    }

    @Test
    void reportsTheConfiguredModelName() {
        // LlmSummaryService stamps summaries with this, so it must be the configured Claude model.
        ChatModel model = anthropicModel("", Duration.ofSeconds(5));

        assertThat(model.defaultRequestParameters().modelName()).isEqualTo(MODEL_NAME);
    }

    @Test
    void appliesConfiguredTimeoutToConnectAndRead() {
        anthropicModel("", Duration.ofMillis(200));

        verify(httpClientBuilder).connectTimeout(Duration.ofMillis(200));
        verify(httpClientBuilder).readTimeout(Duration.ofMillis(200));
    }

    private HttpRequest capturedRequest() {
        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).execute(requestCaptor.capture());
        return requestCaptor.getValue();
    }

    private ChatModel anthropicModel(String baseUrl, Duration timeout) {
        when(httpClientBuilder.build()).thenReturn(httpClient);
        when(httpClient.execute(any(HttpRequest.class)))
                .thenReturn(SuccessfulHttpResponse.builder()
                        .statusCode(200)
                        .body(MESSAGES_RESPONSE)
                        .build());
        LlmProps llmProps = new LlmProps(
                LlmProvider.ANTHROPIC,
                MODEL_NAME,
                Duration.ofMillis(1),
                new LlmProps.Vertex("", ""),
                new LlmProps.Proxy("", new LlmProps.Proxy.Auth(""), Duration.ofSeconds(20)),
                new LlmProps.Anthropic(API_KEY, baseUrl, timeout, 16000),
                new LlmProps.Stub(false));
        return new LlmConfig().anthropicChatModel(llmProps, httpClientBuilder);
    }
}
