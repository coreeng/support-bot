package com.coreeng.supportbot.wiremock;

import com.coreeng.supportbot.Config;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Wiremock implementation for Kubernetes service.
 * Handles mocking of Kubernetes API endpoints.
 */
public class KubernetesWiremock extends WireMockServer {
    private static final Logger logger = LoggerFactory.getLogger(KubernetesWiremock.class);

    private final List<Config.Tenant> tenants;

    public KubernetesWiremock(
        Config.KubernetesMock config,
        List<Config.Tenant> tenants
    ) {
        super(WireMockConfiguration.options().port(config.port()));
        this.tenants = tenants;
    }

    @Override
    public void start() {
        super.start();
        logger.info("Started Kubernetes Wiremock server on port {}", this.port());
    }

    @Override
    public void stop() {
        super.stop();
        logger.info("Stopped Kubernetes Wiremock server");
    }
}
