package com.coreeng.supportbot.wiremock;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wiremock implementation for Azure service.
 * Handles mocking of Azure API endpoints.
 */
public class AzureWiremock extends WireMockServer {
    private static final Logger logger = LoggerFactory.getLogger(AzureWiremock.class);
    private static final int DEFAULT_PORT = 8002;

    /**
     * Creates a new AzureWiremock instance with the default port.
     */
    public AzureWiremock() {
        this(DEFAULT_PORT);
    }

    public AzureWiremock(int port) {
        super(WireMockConfiguration.options().port(port));
    }

    @Override
    public void start() {
        super.start();
        logger.info("Started Azure Wiremock server on port {}", this.port());
    }

    @Override
    public void stop() {
        super.stop();
        logger.info("Stopped Azure Wiremock server");
    }

    public void setupCommonStubs() {
        // This method can be implemented later with specific Azure API stubs
        logger.info("Setting up common Azure API stubs");
    }
}
