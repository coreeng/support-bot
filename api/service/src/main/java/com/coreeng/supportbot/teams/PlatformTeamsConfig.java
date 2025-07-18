package com.coreeng.supportbot.teams;


import com.azure.core.credential.TokenCredential;
import com.coreeng.supportbot.enums.EscalationTeamsRegistry;
import com.coreeng.supportbot.util.JsonMapper;
import com.google.api.client.googleapis.util.Utils;
import com.google.api.gax.core.CredentialsProvider;
import com.google.api.services.cloudidentity.v1.CloudIdentity;
import com.google.auth.http.HttpCredentialsAdapter;
import com.microsoft.graph.core.authentication.AzureIdentityAuthenticationProvider;
import com.microsoft.graph.core.requests.BaseGraphRequestAdapter;
import com.microsoft.graph.core.requests.GraphClientFactory;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.http.HttpClient;
import lombok.RequiredArgsConstructor;
import okhttp3.logging.HttpLoggingInterceptor;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Value;
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
        GenericPlatformTeamsFetcher.Config.class,
        StaticPlatformTeamsProps.class,
        StaticPlatformUsersProps.class
})
@RequiredArgsConstructor
public class PlatformTeamsConfig {
    private final GcpProps gcpProps;
    private final CredentialsProvider gcpCredsProvider;
    private final EscalationTeamsRegistry escalationTeamsRegistry;
    private final StaticPlatformTeamsProps staticPlatformTeamsProps;
    private final StaticPlatformUsersProps staticPlatformUsersProps;

    @Bean
    public PlatformTeamsService platformTeamsService(PlatformTeamsFetcher teamsFetcher, PlatformUsersFetcher usersFetcher) {
        return new PlatformTeamsService(teamsFetcher, usersFetcher, escalationTeamsRegistry);
    }

    @Bean
    @ConditionalOnProperty("platform-integration.teams-scraping.core-platform.enabled")
    public PlatformTeamsFetcher corePlatformTeamsFetcher(KubernetesClient kubernetesClient) {
        return new CorePlatformTeamsFetcher(
                Executors.newVirtualThreadPerTaskExecutor(),
                kubernetesClient
        );
    }

    @Bean
    @ConditionalOnProperty("platform-integration.teams-scraping.static.enabled")
    public PlatformTeamsFetcher localPlatformTeamsFetcher() {
        return new StaticPlatformTeamsFetcher(staticPlatformTeamsProps);
    }

    @Bean
    @ConditionalOnProperty("platform-integration.teams-scraping.k8s-generic.enabled")
    public PlatformTeamsFetcher k8sGenericTeamsFetcher(GenericPlatformTeamsFetcher.Config config, JsonMapper jsonMapper, KubernetesClient kubernetesClient) {
        return new GenericPlatformTeamsFetcher(config, kubernetesClient, jsonMapper);
    }

    @Bean
    @ConditionalOnProperty("platform-integration.gcp.enabled")
    public PlatformUsersFetcher gcpUsersFetcher(
    ) throws IOException {
        var cloundIdentityBuilder = new CloudIdentity.Builder(
                Utils.getDefaultTransport(),
                Utils.getDefaultJsonFactory(),
                new HttpCredentialsAdapter(gcpCredsProvider.getCredentials())
        ).setApplicationName(gcpProps.appName());
        if (Strings.isNotEmpty(gcpProps.client().baseUrl())) {
            cloundIdentityBuilder.setRootUrl(gcpProps.client().baseUrl());
        }
        return new GcpUsersFetcher(cloundIdentityBuilder.build());
    }

    @Bean
    @ConditionalOnProperty("platform-integration.azure.enabled")
    public PlatformUsersFetcher azureUsersFetcher(
            TokenCredential credential,
            @Value("${platform-integration.azure.client.base-url}") String baseUrl
    ) {
        String azureClientLogLevel = System.getenv("AZURE_CLIENT_LOG_LEVEL");
        if (Strings.isBlank(azureClientLogLevel)) {
            azureClientLogLevel = "NONE";
        }
        var client = new GraphServiceClient(
                new BaseGraphRequestAdapter(
                        new AzureIdentityAuthenticationProvider(
                                credential,
                                new String[]{},
                                "https://graph.microsoft.com/.default"
                        ),
                        null,
                        "v1.0",
                        GraphClientFactory.create(GraphServiceClient.getGraphClientOptions())
                                .addInterceptor(
                                        new HttpLoggingInterceptor()
                                                .setLevel(HttpLoggingInterceptor.Level.valueOf(azureClientLogLevel))
                                )
                                .build()
                )
        );
        if (Strings.isNotEmpty(baseUrl)) {
            client.getRequestAdapter().setBaseUrl(baseUrl);
        }
        return new AzureUsersFetcher(client);
    }

    @Bean
    @ConditionalOnProperty("platform-integration.static-user.enabled")
    public PlatformUsersFetcher staticUsersFetcher() {
        return new StaticUsersFetcher(staticPlatformUsersProps);
    }

    @Bean
    public KubernetesClient k8sClient(
            @Value("${platform-integration.kubernetes.base-url}") String baseUrl,
            @Value("${platform-integration.kubernetes.disable-http-proxy}") boolean disableHttpProxy
    ) {
        Config config = Config.autoConfigure(null);

        if (disableHttpProxy) {
            config.setHttpProxy(null);
        }

        if (Strings.isNotEmpty(baseUrl)) {
            config.setMasterUrl(baseUrl);
        }

        KubernetesClientBuilder k8sClientBuilder = new KubernetesClientBuilder()
                .withConfig(config);
        k8sClientBuilder.withHttpClientBuilderConsumer(b -> {
            // required for local runs.
            // for some reason, this client ignores the proxy if it's configured as http, but cluster url is https
            if (config.getHttpProxy() != null && Strings.isBlank(baseUrl)) {
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
            List<String> scopes,
            ClientProps client
    ) {
    }

    public record ClientProps(
            String baseUrl
    ) {
    }
}
