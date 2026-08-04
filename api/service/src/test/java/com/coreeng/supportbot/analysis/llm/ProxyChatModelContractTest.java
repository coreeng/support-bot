package com.coreeng.supportbot.analysis.llm;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coreeng.supportbot.config.AnalysisProps;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.model.chat.ChatModel;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProxyChatModelContractTest {

    private static final String BASE64_TOKEN = "dXNlcjpwYXNz";
    private static final String PROXY_PATH = "/platform/google-vertex/proxy/v1beta";

    private WireMockServer server;

    @BeforeEach
    void startServer() {
        server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Test
    void sendsNativeGeminiRequestWithSingleBasicAuthHeaderAndNoApiKey() {
        String requestPath = PROXY_PATH + "/models/gemini-2.5-flash:generateContent";
        server.stubFor(post(urlPathEqualTo(requestPath)).willReturn(okJson("""
                        {
                          "candidates": [{
                            "content": {"role": "model", "parts": [{"text": "Primary Driver: Knowledge Gap"}]},
                            "finishReason": "STOP"
                          }],
                          "usageMetadata": {"promptTokenCount": 1, "candidatesTokenCount": 1, "totalTokenCount": 2}
                        }
                        """)));

        ChatModel model = proxyModel(Duration.ofSeconds(5));

        String response = model.chat("hello proxy");

        assertThat(response).contains("Primary Driver: Knowledge Gap");
        server.verify(postRequestedFor(urlPathEqualTo(requestPath))
                .withHeader("Content-Type", containing("application/json"))
                .withHeader("Authorization", equalTo("Basic " + BASE64_TOKEN))
                .withoutHeader("x-goog-api-key")
                .withRequestBody(matchingJsonPath("$.contents[0].role", equalTo("user")))
                .withRequestBody(matchingJsonPath("$.contents[0].parts[0].text", containing("hello proxy"))));

        LoggedRequest request = server.getAllServeEvents().getFirst().getRequest();
        assertThat(request.header("Authorization").values()).hasSize(1);
    }

    @Test
    void honoursConfiguredTimeout() {
        server.stubFor(post(urlPathMatching(".*:generateContent"))
                .willReturn(okJson("{}").withFixedDelay(2_000)));

        ChatModel model = proxyModel(Duration.ofMillis(200));

        // Assert the failure kind, not just any exception: an unconfigured timeout would still
        // throw here (the "{}" body has no candidates), but only as a mapping failure after the
        // full 2s delay. No assertion on request count: the client may retry before giving up.
        assertThatThrownBy(() -> model.chat("slow")).isInstanceOf(TimeoutException.class);
    }

    private ChatModel proxyModel(Duration timeout) {
        AnalysisProps.Llm llm = new AnalysisProps.Llm(
                "gemini-2.5-flash",
                Duration.ofMillis(1),
                new AnalysisProps.Vertex(false, "", ""),
                new AnalysisProps.Proxy(
                        true, server.baseUrl() + PROXY_PATH, new AnalysisProps.Proxy.Auth(BASE64_TOKEN), timeout));
        AnalysisProps analysisProps = new AnalysisProps(
                llm,
                new AnalysisProps.Bundle("classpath:placeholder-analysis-bundle.zip"),
                new AnalysisProps.Prompt(true));
        return new LlmConfig().proxyChatModel(analysisProps);
    }
}
