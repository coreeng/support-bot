package com.coreeng.supportbot.wiremock;

import com.coreeng.supportbot.Config;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static java.lang.String.format;

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
        setupAppInitMocks();
        logger.info("Started Azure Wiremock server on port {}", this.port());
    }

    @Override
    public void stop() {
        super.stop();
        logger.info("Stopped Azure Wiremock server");
    }

    public void setupAppInitMocks() {
        logger.info("Setting up initial Azure API stubs");
        for (Config.Tenant tenant : tenants) {
            stubFor(get(format("/v1.0/groups/%s/transitiveMembers/graph.user?$select=mail,accountEnabled,deletedDateTime", tenant.groupRef()))
                .willReturn(okJson(format("""
                    {
                      "@odata.context":"https://graph.microsoft.com/v1.0/$metadata#users(mail,accountEnabled,deletedDateTime)",
                      "value":[
                        %s
                      ]
                    }
                    """, tenant.users().stream()
                    .map(u -> format("""
                        {
                          "mail":"%s",
                          "accountEnabled":true,
                          "deletedDateTime":null
                        }
                        """, u.email()))
                    .collect(Collectors.joining(","))))));
        }
    }
}
