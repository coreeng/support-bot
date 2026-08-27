package com.coreeng.supportbot.analysis.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.coreeng.supportbot.config.AnalysisProps;
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
import org.mockito.ArgumentCaptor;

/**
 * Pins the wire contract with the LLM proxy at langchain4j's {@link HttpClient} boundary: the
 * request the client would put on the wire (URL, auth header, native Gemini body) and how our
 * single timeout setting maps onto the transport. No server involved; actually sending the
 * request is the HTTP client's responsibility.
 */
class ProxyChatModelContractTest {

    private static final String BASE64_TOKEN = "dXNlcjpwYXNz";
    private static final String BASE_URL = "https://llm-proxy.example.test/platform/google-vertex/proxy/v1beta";

    private static final String GEMINI_RESPONSE = """
            {
              "candidates": [{
                "content": {"role": "model", "parts": [{"text": "Primary Driver: Knowledge Gap"}]},
                "finishReason": "STOP"
              }],
              "usageMetadata": {"promptTokenCount": 1, "candidatesTokenCount": 1, "totalTokenCount": 2}
            }
            """;

    // RETURNS_SELF keeps the fluent setter chain working however the client composes it.
    private final HttpClientBuilder httpClientBuilder = mock(HttpClientBuilder.class, RETURNS_SELF);
    private final HttpClient httpClient = mock(HttpClient.class);

    @Test
    void sendsNativeGeminiRequestWithSingleBasicAuthHeaderAndNoApiKey() throws Exception {
        when(httpClient.execute(any(HttpRequest.class)))
                .thenReturn(SuccessfulHttpResponse.builder()
                        .statusCode(200)
                        .body(GEMINI_RESPONSE)
                        .build());
        ChatModel model = proxyModel(Duration.ofSeconds(5));

        String response = model.chat("hello proxy");

        assertThat(response).contains("Primary Driver: Knowledge Gap");

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).execute(requestCaptor.capture());
        HttpRequest request = requestCaptor.getValue();
        assertThat(request.method()).isEqualTo(HttpMethod.POST);
        assertThat(request.url()).isEqualTo(BASE_URL + "/models/gemini-2.5-flash:generateContent");
        assertThat(request.headers().get("Authorization")).containsExactly("Basic " + BASE64_TOKEN);
        assertThat(request.headers().keySet()).noneSatisfy(header -> assertThat(header.toLowerCase(Locale.ROOT))
                .isEqualTo("x-goog-api-key"));

        JsonNode body = new ObjectMapper().readTree(request.body());
        assertThat(body.at("/contents/0/role").asText()).isEqualTo("user");
        assertThat(body.at("/contents/0/parts/0/text").asText()).contains("hello proxy");
    }

    @Test
    void appliesConfiguredTimeoutToConnectAndRead() {
        proxyModel(Duration.ofMillis(200));

        // Our single timeout setting must reach both transport knobs; whether the transport then
        // enforces them is the HTTP client's responsibility, not asserted here.
        verify(httpClientBuilder).connectTimeout(Duration.ofMillis(200));
        verify(httpClientBuilder).readTimeout(Duration.ofMillis(200));
    }

    private ChatModel proxyModel(Duration timeout) {
        when(httpClientBuilder.build()).thenReturn(httpClient);
        AnalysisProps.Llm llm = new AnalysisProps.Llm(
                "gemini-2.5-flash",
                Duration.ofMillis(1),
                new AnalysisProps.Vertex(false, "", ""),
                new AnalysisProps.Proxy(true, BASE_URL, new AnalysisProps.Proxy.Auth(BASE64_TOKEN), timeout),
                new AnalysisProps.Stub(false));
        AnalysisProps analysisProps = new AnalysisProps(
                llm,
                new AnalysisProps.Bundle("classpath:placeholder-analysis-bundle.zip"),
                new AnalysisProps.Prompt(true));
        return new LlmConfig().proxyChatModel(analysisProps, httpClientBuilder);
    }
}
