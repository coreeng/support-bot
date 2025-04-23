package com.coreeng.supportbot.teams;


import com.coreeng.supportbot.enums.EscalationTeamsRegistry;
import com.coreeng.supportbot.util.JsonMapper;
import com.google.api.client.googleapis.util.Utils;
import com.google.api.gax.core.CredentialsProvider;
import com.google.api.services.cloudidentity.v1.CloudIdentity;
import com.google.auth.http.HttpCredentialsAdapter;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.http.HttpClient;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.concurrent.Executors;

@Configuration
@ConditionalOnProperty(
    name = {"enabled"},
    prefix = "platform-integration"
)
@EnableConfigurationProperties({
    PlatformTeamsConfig.GcpProps.class,
    GenericPlatformTeamsFetcher.Config.class
})
@RequiredArgsConstructor
public class PlatformTeamsConfig {
    private final GcpProps gcpProps;
    private final CredentialsProvider gcpCredsProvider;
    private final EscalationTeamsRegistry escalationTeamsRegistry;

    @Bean
    public PlatformTeamsService platformTeamsService(PlatformTeamsFetcher teamsFetcher) throws IOException {
        return new PlatformTeamsService(teamsFetcher, usersFetcher(), escalationTeamsRegistry);
    }

    @Bean
    @ConditionalOnProperty("platform-integration.teams-scraping.core-platform.enabled")
    public PlatformTeamsFetcher corePlatformTeamsFetcher() {
        return new CorePlatformTeamsFetcher(
            Executors.newVirtualThreadPerTaskExecutor(),
            k8sClient()
        );
    }

    @Bean
    @ConditionalOnProperty("platform-integration.teams-scraping.k8s-generic.enabled")
    public PlatformTeamsFetcher k8sGenericTeamsFetcher(GenericPlatformTeamsFetcher.Config config, JsonMapper jsonMapper) {
        return new GenericPlatformTeamsFetcher(config, k8sClient(), jsonMapper);
    }

    @Bean
    public PlatformUsersFetcher usersFetcher() throws IOException {
        return new GcpUsersFetcher(cloudIdentity());
    }

    @Bean
    public CloudIdentity cloudIdentity() throws IOException {
        return new CloudIdentity.Builder(
            Utils.getDefaultTransport(),
            Utils.getDefaultJsonFactory(),
            new HttpCredentialsAdapter(gcpCredsProvider.getCredentials())
        ).setApplicationName(gcpProps.appName())
            .build();
    }

    @Bean
    public KubernetesClient k8sClient() {
        Config config = Config.autoConfigure(null);
        KubernetesClientBuilder k8sClientBuilder = new KubernetesClientBuilder();
        k8sClientBuilder.withHttpClientBuilderConsumer(b -> {
            // required for local runs.
            // for some reason, this client ignores the proxy if it's configured as http, but cluster url is https
            if (config.getHttpProxy() != null) {
                URI httpProxy = URI.create(config.getHttpProxy());
                b.proxyAddress(InetSocketAddress.createUnresolved(httpProxy.getHost(), httpProxy.getPort()))
                    .proxyType(HttpClient.ProxyType.HTTP);
            }
        });
        return k8sClientBuilder.build();
    }

    @ConfigurationProperties("platform-integration.gcp")
    record GcpProps(
        String appName,
        List<String> scopes
    ) {
    }
}
