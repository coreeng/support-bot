package com.coreeng.supportbot.wiremock;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wiremock implementation for GCP (Google Cloud Platform) service.
 * Handles mocking of GCP API endpoints.
 */
public class GcpWiremock extends WireMockServer {
    private static final Logger logger = LoggerFactory.getLogger(GcpWiremock.class);
    private static final int DEFAULT_PORT = 8003;

    /**
     * Creates a new GcpWiremock instance with the default port.
     */
    public GcpWiremock() {
        this(DEFAULT_PORT);
    }

    public GcpWiremock(int port) {
        super(WireMockConfiguration.options().port(port));
    }

    @Override
    public void start() {
        super.start();
        logger.info("Started GCP Wiremock server on port {}", port());
    }

    @Override
    public void stop() {
        super.stop();
        logger.info("Stopped GCP Wiremock server");
    }
}
