package com.coreeng.supportbot.testkit;

import static org.assertj.core.api.Assertions.assertThat;
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

    public void assertIsCalled(String message) {
        GetServeEventsResult serveEvents = wireMockServer.getServeEvents(ServeEventQuery.forStubMapping(mapping));
        assertThat(serveEvents.getServeEvents())
            .as("%s: stub was called exactly once", message)
            .hasSize(1);
    }
}
