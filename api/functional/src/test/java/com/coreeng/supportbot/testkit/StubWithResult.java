package com.coreeng.supportbot.testkit;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.admin.model.GetServeEventsResult;
import com.github.tomakehurst.wiremock.admin.model.ServeEventQuery;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import lombok.Builder;
import lombok.Getter;
import org.jspecify.annotations.NonNull;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

@Builder
@Getter
public class StubWithResult<T> {
    @NonNull
    private final StubMapping mapping;
    @NonNull
    private final WireMockServer wireMockServer;
    @NonNull
    private final Receiver<T> receiver;

    private T result;
    private boolean resultCalculated;
    private boolean asserted;

    public void assertIsCalled(String message) {
        if (asserted) {
            return;
        }
        GetServeEventsResult serveEvents = wireMockServer.getServeEvents(ServeEventQuery.forStubMapping(mapping));
        assertThat(serveEvents.getServeEvents())
            .as("%s: stub was called exactly once", message)
            .hasSize(1);
        assertThatNoException()
            .as("%s: stub returned a result", message)
            .isThrownBy(() ->
            result = receiver.assertAndExtractResult(serveEvents.getServeEvents().getFirst())
        );
        assertThat(result)
            .as("%s: stub returned a non-null result", message)
            .isNotNull();
        resultCalculated = true;
        asserted = true;
        clean(serveEvents);
    }

    public void assertIsNotCalled(String message) {
        if (asserted) {
            return;
        }
        GetServeEventsResult serveEvents = wireMockServer.getServeEvents(ServeEventQuery.forStubMapping(mapping));
        assertThat(serveEvents.getServeEvents())
            .as("%s: stub should not have been called", message)
            .isEmpty();
        asserted = true;
        clean(serveEvents);
    }

    public T result() {
        assertThat(resultCalculated).isTrue();
        return result;
    }

    public void clean() {
        GetServeEventsResult serveEvents = wireMockServer.getServeEvents(ServeEventQuery.forStubMapping(mapping));
        clean(serveEvents);
    }

    private void clean(GetServeEventsResult serveEvents) {
        wireMockServer.removeStubMapping(mapping);
        for (ServeEvent serveEvent : serveEvents.getServeEvents()) {
            wireMockServer.removeStubMapping(serveEvent.getStubMapping());
        }
    }

    public interface Receiver<T> {
        MappingBuilder configureStub(MappingBuilder stubBuilder);

        T assertAndExtractResult(ServeEvent servedStub) throws Exception;
    }
}
