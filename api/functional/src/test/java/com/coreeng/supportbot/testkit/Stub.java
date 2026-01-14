package com.coreeng.supportbot.testkit;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import org.jspecify.annotations.NonNull;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.admin.model.GetServeEventsResult;
import com.github.tomakehurst.wiremock.admin.model.ServeEventQuery;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class Stub {
    @NonNull
    private final StubMapping mapping;
    @NonNull
    private final WireMockServer wireMockServer;

    private boolean asserted;

    public void assertIsCalled(String message) {
        if (asserted) {
            return;
        }
        GetServeEventsResult serveEvents = wireMockServer.getServeEvents(ServeEventQuery.forStubMapping(mapping));
        assertThat(serveEvents.getServeEvents())
            .as("%s: stub was called exactly once", message)
            .hasSize(1);
        asserted = true;

        clean(serveEvents);
    }

    public void assertIsNotCalled(String message) {
        if (!asserted) {
            return;
        }
        GetServeEventsResult serveEvents = wireMockServer.getServeEvents(ServeEventQuery.forStubMapping(mapping));
        assertThat(serveEvents.getServeEvents())
            .as("%s: stub should not have been called", message)
            .isEmpty();
        asserted = true;

        clean(serveEvents);
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
}
