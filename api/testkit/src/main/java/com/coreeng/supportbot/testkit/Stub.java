package com.coreeng.supportbot.testkit;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Getter
public class Stub {
    private static final Logger LOGGER = LoggerFactory.getLogger(Stub.class);

    @NonNull private final StubMapping mapping;

    @NonNull private final List<StubMapping> extraMappings;

    @NonNull private final WireMockBackend wireMockServer;

    @NonNull private final String description;

    private boolean asserted;
    private boolean cleanedUp;

    @Builder
    public Stub(
            @NonNull StubMapping mapping,
            List<StubMapping> extraMappings,
            @NonNull WireMockBackend wireMockServer,
            @NonNull String description) {
        this.mapping = mapping;
        this.extraMappings = extraMappings == null ? Collections.emptyList() : extraMappings;
        this.wireMockServer = wireMockServer;
        this.description = description;
    }

    public void assertIsCalled() {
        assertIsCalled(1);
    }

    public void assertIsCalled(int expectedCount) {
        if (asserted) {
            LOGGER.debug("Stub '{}' already asserted, skipping", description);
            return;
        }
        List<?> serveEvents = wireMockServer.getServeEventsFor(mapping);
        assertThat(serveEvents)
                .as("%s: stub was called exactly %d times", description, expectedCount)
                .hasSize(expectedCount);
        asserted = true;

        cleanUp();
    }

    public void assertIsNotCalled() {
        if (asserted) {
            LOGGER.debug("Stub '{}' already asserted, skipping", description);
            return;
        }
        assertThat(wireMockServer.getServeEventsFor(mapping))
                .as("%s: stub should not have been called", description)
                .isEmpty();
    }

    public void cleanUp() {
        if (cleanedUp) {
            LOGGER.debug("Stub '{}' already cleaned up, skipping", description);
            return;
        }
        wireMockServer.removeStubMapping(mapping);
        for (StubMapping extra : extraMappings) {
            wireMockServer.removeStubMapping(extra);
        }
        cleanedUp = true;
    }
}
