package com.coreeng.supportbot.testkit;

import static org.assertj.core.api.Assertions.assertThat;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.admin.model.GetServeEventsResult;
import com.github.tomakehurst.wiremock.admin.model.ServeEventQuery;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;

import lombok.Builder;
import lombok.Getter;

@Getter
public class Stub {
    private static final Logger logger = LoggerFactory.getLogger(Stub.class);

    @NonNull
    private final StubMapping mapping;
    @NonNull
    private final WireMockServer wireMockServer;
    @NonNull
    private final String description;

    private boolean asserted;

    @Builder
    public Stub(@NonNull StubMapping mapping, @NonNull WireMockServer wireMockServer, @NonNull String description) {
        this.mapping = mapping;
        this.wireMockServer = wireMockServer;
        this.description = description;
    }

    public void assertIsCalled() {
        if (asserted) {
            logger.debug("Stub '{}' already asserted, skipping", description);
            return;
        }
        GetServeEventsResult serveEvents = wireMockServer.getServeEvents(ServeEventQuery.forStubMapping(mapping));
        assertThat(serveEvents.getServeEvents())
            .as("%s: stub was called exactly once", description)
            .hasSize(1);
        asserted = true;

        cleanUp();
    }

    public void assertIsNotCalled() {
        if (asserted) {
            logger.debug("Stub '{}' already asserted, skipping", description);
            return;
        }
        GetServeEventsResult serveEvents = wireMockServer.getServeEvents(ServeEventQuery.forStubMapping(mapping));
        assertThat(serveEvents.getServeEvents())
            .as("%s: stub should not have been called", description)
            .isEmpty();
    }

    public void cleanUp() {
        wireMockServer.removeStubMapping(mapping);
    }
}
