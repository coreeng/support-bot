package com.coreeng.supportbot.security;

import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.ServerConnector;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.embedded.jetty.JettyServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Adds the internal listener that Elevate's erasure orchestrator calls (ADR-010). */
@Configuration
@ConditionalOnProperty(name = "internal-port.enabled", havingValue = "true")
@EnableConfigurationProperties(InternalPortProperties.class)
public class InternalPortConnectorConfig {

    public static final String CONNECTOR_NAME = "internal";

    @Bean
    public WebServerFactoryCustomizer<JettyServletWebServerFactory> internalPortConnector(
            InternalPortProperties properties) {
        return factory -> factory.addServerCustomizers(server -> {
            HttpConfiguration httpConfiguration = new HttpConfiguration();
            httpConfiguration.setSendServerVersion(false);
            ServerConnector connector = new ServerConnector(server, new HttpConnectionFactory(httpConfiguration));
            connector.setName(CONNECTOR_NAME);
            connector.setPort(properties.port());
            server.addConnector(connector);
        });
    }
}
