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
import dev.langchain4j.model.chat.ChatModel;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GatewayChatModelContractTest {

    private static final String BASE64_TOKEN = "dXNlcjpwYXNz";
    private static final String GATEWAY_PATH = "/platform/google-vertex/proxy/v1beta";

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
        String requestPath = GATEWAY_PATH + "/models/gemini-2.5-flash:generateContent";
        server.stubFor(post(urlPathEqualTo(requestPath)).willReturn(okJson("""
                        {
                          "candidates": [{
                            "content": {"role": "model", "parts": [{"text": "Primary Driver: Knowledge Gap"}]},
                            "finishReason": "STOP"
                          }],
                          "usageMetadata": {"promptTokenCount": 1, "candidatesTokenCount": 1, "totalTokenCount": 2}
                        }
                        """)));

        ChatModel model = gatewayModel(Duration.ofSeconds(5));

        String response = model.chat("hello gateway");

        assertThat(response).contains("Primary Driver: Knowledge Gap");
        server.verify(postRequestedFor(urlPathEqualTo(requestPath))
                .withHeader("Content-Type", containing("application/json"))
                .withHeader("Authorization", equalTo("Basic " + BASE64_TOKEN))
                .withoutHeader("x-goog-api-key")
                .withRequestBody(matchingJsonPath("$.contents[0].role", equalTo("user")))
                .withRequestBody(matchingJsonPath("$.contents[0].parts[0].text", containing("hello gateway"))));

        LoggedRequest request = server.getAllServeEvents().getFirst().getRequest();
        assertThat(request.header("Authorization").values()).hasSize(1);
    }

    @Test
    void honoursConfiguredTimeout() {
        server.stubFor(post(urlPathMatching(".*:generateContent"))
                .willReturn(okJson("{}").withFixedDelay(2_000)));

        ChatModel model = gatewayModel(Duration.ofMillis(200));

        // Assert only on failure, not request count: the client may retry before giving up.
        assertThatThrownBy(() -> model.chat("slow")).isInstanceOf(RuntimeException.class);
    }

    private ChatModel gatewayModel(Duration timeout) {
        AnalysisProps.Llm llm = new AnalysisProps.Llm(
                AnalysisProps.LlmProvider.GATEWAY,
                "gemini-2.5-flash",
                Duration.ofMillis(1),
                new AnalysisProps.Vertex("", ""),
                new AnalysisProps.Gateway(
                        new AnalysisProps.Gateway.Proxy(
                                new AnalysisProps.Gateway.GoogleVertex(server.baseUrl() + GATEWAY_PATH)),
                        new AnalysisProps.Gateway.Auth(BASE64_TOKEN),
                        timeout));
        AnalysisProps analysisProps = new AnalysisProps(
                llm,
                new AnalysisProps.Bundle("classpath:placeholder-analysis-bundle.zip"),
                new AnalysisProps.Prompt(true));
        return new LlmConfig().gatewayChatModel(analysisProps);
    }
}
