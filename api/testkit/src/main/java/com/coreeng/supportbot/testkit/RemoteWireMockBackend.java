package com.coreeng.supportbot.testkit;

import com.github.tomakehurst.wiremock.client.HttpAdminClient;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.util.List;

final class RemoteWireMockBackend implements WireMockBackend {
    private final Config.SlackMock config;
    private final HttpAdminClient adminClient;
    private final WireMock wireMock;

    RemoteWireMockBackend(Config.SlackMock config) {
        this.config = config;
        adminClient = new HttpAdminClient(
                config.remoteAdminSchemeOrDefault(),
                config.remoteAdminHostOrDefault(),
                config.remoteAdminPortOrDefault());
        wireMock = new WireMock(adminClient);
    }

    @Override
    public void start() {}

    @Override
    public void stop() {}

    @Override
    public int port() {
        return config.remoteAdminPortOrDefault();
    }

    @Override
    public void resetAll() {
        adminClient.resetAll();
    }

    @Override
    public void resetRequests() {
        adminClient.resetRequests();
    }

    @Override
    public StubMapping givenThat(MappingBuilder mappingBuilder) {
        return wireMock.register(mappingBuilder);
    }

    @Override
    public void removeStubMapping(StubMapping stubMapping) {
        adminClient.removeStubMapping(stubMapping);
    }

    @Override
    public List<StubMapping> getStubMappings() {
        return adminClient.listAllStubMappings().getMappings();
    }

    @Override
    public List<ServeEvent> getServeEventsFor(StubMapping stubMapping) {
        return adminClient
                .getServeEvents(com.github.tomakehurst.wiremock.admin.model.ServeEventQuery.forStubMapping(stubMapping))
                .getServeEvents();
    }

    @Override
    public List<ServeEvent> getAllServeEvents() {
        return adminClient.getServeEvents().getServeEvents();
    }

    @Override
    public List<LoggedRequest> findAll(RequestPatternBuilder requestPatternBuilder) {
        return adminClient.findRequestsMatching(requestPatternBuilder.build()).getRequests();
    }

    @Override
    public List<LoggedRequest> findAllUnmatchedRequests() {
        return adminClient.findUnmatchedRequests().getRequests();
    }
}
