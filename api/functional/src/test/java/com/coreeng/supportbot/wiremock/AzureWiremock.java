package com.coreeng.supportbot.wiremock;

import com.coreeng.supportbot.Config;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Wiremock implementation for Azure service.
 * Handles mocking of Azure API endpoints.
 */
public class AzureWiremock extends WireMockServer {
    private static final Logger logger = LoggerFactory.getLogger(AzureWiremock.class);

    private final List<Config.Tenant> tenants;

    public AzureWiremock(Config.AzureMock config, List<Config.Tenant> tenants) {
        super(WireMockConfiguration.options().port(config.port()));
        this.tenants = tenants;
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
}
