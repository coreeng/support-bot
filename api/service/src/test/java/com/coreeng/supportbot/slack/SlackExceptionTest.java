package com.coreeng.supportbot.slack;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableList;
import com.slack.api.methods.SlackApiErrorResponse;
import com.slack.api.methods.SlackApiException;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.Test;

class SlackExceptionTest {

    @Test
    void shouldExtractRetryAfterFromSlackApiException() {
        // Given
        SlackApiException cause = rateLimitedException("30");

        // When
        SlackException exception = new SlackException(cause);

        // Then
        assertThat(exception.retryAfterSeconds()).isEqualTo(30);
        assertThat(exception.getError()).isEqualTo("ratelimited");
    }

    @Test
    void shouldReturnNullRetryAfterWhenHeaderMissing() {
        // Given
        Response okHttpResponse = responseBuilder(429).build();
        SlackApiException cause = new SlackApiException(okHttpResponse, "{\"ok\":false,\"error\":\"ratelimited\"}");

        // When
        SlackException exception = new SlackException(cause);

        // Then
        assertThat(exception.retryAfterSeconds()).isNull();
    }

    @Test
    void shouldReturnNullRetryAfterWhenHeaderMalformed() {
        // Given
        SlackApiException cause = rateLimitedException("not-a-number");

        // When
        SlackException exception = new SlackException(cause);

        // Then
        assertThat(exception.retryAfterSeconds()).isNull();
    }

    @Test
    void shouldReturnNullRetryAfterForNonSlackApiException() {
        // Given
        RuntimeException cause = new RuntimeException("some error");

        // When
        SlackException exception = new SlackException(cause);

        // Then
        assertThat(exception.retryAfterSeconds()).isNull();
    }

    @Test
    void shouldReturnNullRetryAfterWhenValueIsZero() {
        // Given
        SlackApiException cause = rateLimitedException("0");

        // When
        SlackException exception = new SlackException(cause);

        // Then
        assertThat(exception.retryAfterSeconds()).isNull();
    }

    @Test
    void shouldReturnNullRetryAfterWhenValueIsNegative() {
        // Given
        SlackApiException cause = rateLimitedException("-5");

        // When
        SlackException exception = new SlackException(cause);

        // Then
        assertThat(exception.retryAfterSeconds()).isNull();
    }

    @Test
    void shouldReturnNullRetryAfterForResponseConstructor() {
        // Given
        SlackApiErrorResponse errorResponse = new SlackApiErrorResponse();

        // When
        SlackException exception = new SlackException(errorResponse, ImmutableList.of());

        // Then
        assertThat(exception.retryAfterSeconds()).isNull();
    }

    private static SlackApiException rateLimitedException(String retryAfterValue) {
        Response okHttpResponse =
                responseBuilder(429).header("Retry-After", retryAfterValue).build();
        return new SlackApiException(okHttpResponse, "{\"ok\":false,\"error\":\"ratelimited\"}");
    }

    private static Response.Builder responseBuilder(int code) {
        return new Response.Builder()
                .request(new Request.Builder().url("https://slack.com/api/test").build())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("Too Many Requests");
    }
}
