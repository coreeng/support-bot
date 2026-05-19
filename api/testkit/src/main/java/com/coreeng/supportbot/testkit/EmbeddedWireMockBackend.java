package com.coreeng.supportbot.testkit;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.util.List;

final class EmbeddedWireMockBackend implements WireMockBackend {
    private final WireMockServer wireMockServer;

    EmbeddedWireMockBackend(Config.SlackMock config) {
        wireMockServer = new WireMockServer(WireMockConfiguration.options()
                .port(config.port())
                .globalTemplating(true)
                .maxRequestJournalEntries(1000));
    }

    @Override
    public void start() {
        wireMockServer.start();
    }

    @Override
    public void stop() {
        wireMockServer.stop();
    }

    @Override
    public int port() {
        return wireMockServer.port();
    }

    @Override
    public void resetAll() {
        wireMockServer.resetAll();
    }

    @Override
    public void resetRequests() {
        wireMockServer.resetRequests();
    }

    @Override
    public StubMapping givenThat(MappingBuilder mappingBuilder) {
        return wireMockServer.givenThat(mappingBuilder);
    }

    @Override
    public void removeStubMapping(StubMapping stubMapping) {
        wireMockServer.removeStubMapping(stubMapping);
    }

    @Override
    public List<StubMapping> getStubMappings() {
        return wireMockServer.listAllStubMappings().getMappings();
    }

    @Override
    public List<ServeEvent> getServeEventsFor(StubMapping stubMapping) {
        return wireMockServer
                .getServeEvents(com.github.tomakehurst.wiremock.admin.model.ServeEventQuery.forStubMapping(stubMapping))
                .getServeEvents();
    }

    @Override
    public List<ServeEvent> getAllServeEvents() {
        return wireMockServer.getAllServeEvents();
    }

    @Override
    public List<LoggedRequest> findAll(RequestPatternBuilder requestPatternBuilder) {
        return wireMockServer.findAll(requestPatternBuilder);
    }

    @Override
    public List<LoggedRequest> findAllUnmatchedRequests() {
        return wireMockServer.findAllUnmatchedRequests();
    }
}
