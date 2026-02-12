package com.coreeng.supportbot.testkit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.admin.model.GetServeEventsResult;
import com.github.tomakehurst.wiremock.admin.model.ServeEventQuery;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import lombok.Builder;
import lombok.Getter;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Getter
public class StubWithResult<T> {
    private static final Logger LOGGER = LoggerFactory.getLogger(StubWithResult.class);

    @NonNull private final StubMapping mapping;

    @NonNull private final WireMockServer wireMockServer;

    @NonNull private final Receiver<T> receiver;

    @NonNull private final String description;

    private T result;
    private boolean resultCalculated;
    private boolean asserted;

    @Builder
    public StubWithResult(
            @NonNull StubMapping mapping,
            @NonNull WireMockServer wireMockServer,
            @NonNull Receiver<T> receiver,
            @NonNull String description) {
        this.mapping = mapping;
        this.wireMockServer = wireMockServer;
        this.receiver = receiver;
        this.description = description;
    }

    public void assertIsCalled() {
        if (asserted) {
            LOGGER.debug("Stub '{}' already asserted, skipping", description);
            return;
        }
        GetServeEventsResult serveEvents = wireMockServer.getServeEvents(ServeEventQuery.forStubMapping(mapping));
        assertThat(serveEvents.getServeEvents())
                .as("%s: stub was called exactly once", description)
                .hasSize(1);
        assertThatNoException()
                .as("%s: stub returned a result", description)
                .isThrownBy(() -> result = receiver.assertAndExtractResult(
                        serveEvents.getServeEvents().getFirst()));
        assertThat(result)
                .as("%s: stub returned a non-null result", description)
                .isNotNull();
        resultCalculated = true;
        asserted = true;
        cleanUp();
    }

    public void assertIsNotCalled() {
        if (asserted) {
            LOGGER.debug("Stub '{}' already asserted, skipping", description);
            return;
        }
        GetServeEventsResult serveEvents = wireMockServer.getServeEvents(ServeEventQuery.forStubMapping(mapping));
        assertThat(serveEvents.getServeEvents())
                .as("%s: stub should not have been called", description)
                .isEmpty();
    }

    public T result() {
        assertThat(resultCalculated).isTrue();
        return result;
    }

    public void cleanUp() {
        wireMockServer.removeStubMapping(mapping);
    }

    public interface Receiver<T> {
        MappingBuilder configureStub(MappingBuilder stubBuilder);

        T assertAndExtractResult(ServeEvent servedStub) throws Exception;
    }
}
