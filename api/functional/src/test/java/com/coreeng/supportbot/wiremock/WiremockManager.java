package com.coreeng.supportbot.wiremock;

import com.coreeng.supportbot.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manager class for all Wiremock instances.
 * Provides methods to start and stop all Wiremock servers.
 */
public class WiremockManager {
    private static final Logger logger = LoggerFactory.getLogger(WiremockManager.class);

    public final SlackWiremock slackWiremock;
    public final KubernetesWiremock kubernetesWiremock;
    public final AzureWiremock azureWiremock;
    public final GcpWiremock gcpWiremock;

    public WiremockManager(Config config) {
        slackWiremock = new SlackWiremock(config.mocks().slack());
        kubernetesWiremock = new KubernetesWiremock(config.mocks().kubernetes(), config.tenants());
        azureWiremock = new AzureWiremock();
        gcpWiremock = new GcpWiremock();
    }

    public void startAll() {
        slackWiremock.start();
        kubernetesWiremock.start();
        azureWiremock.start();
        gcpWiremock.start();
        logger.info("All Wiremock servers started");
    }

    public void stopAll() {
        slackWiremock.stop();
        kubernetesWiremock.stop();
        azureWiremock.stop();
        gcpWiremock.stop();
        logger.info("All Wiremock servers stopped");
    }

    public void resetAll() {
        slackWiremock.resetAll();
        kubernetesWiremock.resetAll();
        azureWiremock.resetAll();
        gcpWiremock.resetAll();
        logger.info("All Wiremock servers reset");
    }
}
