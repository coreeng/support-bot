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

    public void assertIsCalled() {
        GetServeEventsResult serveEvents = wireMockServer.getServeEvents(ServeEventQuery.forStubMapping(mapping));
        assertThat(serveEvents.getServeEvents()).hasSize(1);
        assertThatNoException().isThrownBy(() ->
            result = receiver.extractResult(serveEvents.getServeEvents().getFirst())
        );
        assertThat(result).isNotNull();
        resultCalculated = true;
    }

    public T result() {
        assertThat(resultCalculated).isTrue();
        return result;
    }

    public interface Receiver<T> {
        MappingBuilder configureStub(MappingBuilder stubBuilder);

        T extractResult(ServeEvent servedStub) throws Exception;
    }
}
