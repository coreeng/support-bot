package com.coreeng.supportbot.testkit;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.util.List;

interface WireMockBackend {
    void start();

    void stop();

    int port();

    void resetAll();

    void resetRequests();

    StubMapping givenThat(MappingBuilder mappingBuilder);

    void removeStubMapping(StubMapping stubMapping);

    List<StubMapping> getStubMappings();

    List<ServeEvent> getServeEventsFor(StubMapping stubMapping);

    List<ServeEvent> getAllServeEvents();

    List<LoggedRequest> findAll(RequestPatternBuilder requestPatternBuilder);

    List<LoggedRequest> findAllUnmatchedRequests();
}
