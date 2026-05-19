package com.coreeng.supportbot.testkit;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

class StubCleanupTest {
    @Test
    void stubCleanupIsIdempotent() {
        FakeWireMockBackend backend = new FakeWireMockBackend();
        StubMapping mapping = new StubMapping();

        Stub stub = Stub.builder()
                .mapping(mapping)
                .wireMockServer(backend)
                .description("idempotent stub cleanup")
                .build();

        stub.cleanUp();
        stub.cleanUp();

        assertThat(backend.removedMappings()).containsExactly(mapping);
    }

    @Test
    void stubWithResultCleanupIsIdempotent() {
        FakeWireMockBackend backend = new FakeWireMockBackend();
        StubMapping mapping = new StubMapping();

        StubWithResult<Boolean> stub = StubWithResult.<Boolean>builder()
                .mapping(mapping)
                .wireMockServer(backend)
                .receiver(new StubWithResult.Receiver<>() {
                    @Override
                    public MappingBuilder configureStub(MappingBuilder stubBuilder) {
                        return stubBuilder;
                    }

                    @Override
                    public Boolean assertAndExtractResult(ServeEvent servedStub) {
                        return true;
                    }
                })
                .description("idempotent stub-with-result cleanup")
                .build();

        stub.cleanUp();
        stub.cleanUp();

        assertThat(backend.removedMappings()).containsExactly(mapping);
    }

    private static final class FakeWireMockBackend implements WireMockBackend {
        private final java.util.ArrayList<StubMapping> removedMappings = new java.util.ArrayList<>();

        List<StubMapping> removedMappings() {
            return removedMappings;
        }

        @Override
        public void start() {}

        @Override
        public void stop() {}

        @Override
        public int port() {
            return 0;
        }

        @Override
        public void resetAll() {}

        @Override
        public void resetRequests() {}

        @Override
        public StubMapping givenThat(MappingBuilder mappingBuilder) {
            return new StubMapping();
        }

        @Override
        public void removeStubMapping(StubMapping stubMapping) {
            removedMappings.add(stubMapping);
        }

        @Override
        public List<StubMapping> getStubMappings() {
            return List.of();
        }

        @Override
        public List<ServeEvent> getServeEventsFor(StubMapping stubMapping) {
            return List.of();
        }

        @Override
        public List<ServeEvent> getAllServeEvents() {
            return List.of();
        }

        @Override
        public List<LoggedRequest> findAll(
                com.github.tomakehurst.wiremock.matching.RequestPatternBuilder requestPatternBuilder) {
            return List.of();
        }

        @Override
        public List<LoggedRequest> findAllUnmatchedRequests() {
            return List.of();
        }
    }
}
