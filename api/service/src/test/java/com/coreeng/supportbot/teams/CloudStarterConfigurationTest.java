package com.coreeng.supportbot.teams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.azure.core.credential.TokenCredential;
import com.azure.identity.ClientSecretCredential;
import com.azure.spring.cloud.autoconfigure.implementation.context.AzureGlobalPropertiesAutoConfiguration;
import com.azure.spring.cloud.autoconfigure.implementation.context.AzureTokenCredentialAutoConfiguration;
import com.coreeng.supportbot.enums.EscalationTeamsRegistry;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.coreeng.supportbot.teams.groups.GroupResolver;
import com.coreeng.supportbot.util.JsonMapper;
import com.google.api.gax.core.CredentialsProvider;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.spring.autoconfigure.core.GcpContextAutoConfiguration;
import com.google.common.collect.ImmutableList;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CloudStarterConfigurationTest {
    private static final String READONLY_SCOPE = "https://www.googleapis.com/auth/cloud-identity.groups.readonly";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PlatformTeamsConfig.class)
            .withBean(EscalationTeamsRegistry.class, () -> {
                EscalationTeamsRegistry registry = mock(EscalationTeamsRegistry.class);
                when(registry.listAllEscalationTeams()).thenReturn(ImmutableList.of());
                return registry;
            })
            .withBean(GroupResolver.class, () -> mock(GroupResolver.class))
            .withBean(SlackClient.class, () -> mock(SlackClient.class))
            .withBean(ExecutorService.class, Executors::newVirtualThreadPerTaskExecutor)
            .withBean(PlatformTeamsFetcher.class, () -> {
                PlatformTeamsFetcher fetcher = mock(PlatformTeamsFetcher.class);
                when(fetcher.fetchTeams()).thenReturn(List.of());
                return fetcher;
            })
            .withPropertyValues(
                    "platform-integration.enabled=true",
                    "platform-integration.fetch.max-concurrency=1",
                    "platform-integration.fetch.timeout=PT1S",
                    "platform-integration.kubernetes.base-url=http://127.0.0.1:9/",
                    "platform-integration.kubernetes.disable-http-proxy=true",
                    "platform-integration.azure.client.base-url=http://127.0.0.1:9/v1.0",
                    "platform-integration.azure.client.log-level=NONE",
                    "platform-integration.gcp.app-name=SupportBotTest",
                    "platform-integration.gcp.client.base-url=http://127.0.0.1:9/");

    @Test
    void enabledGcpStarterWiresReadonlyCredentialsAndCloudIdentityWithoutNetwork() throws Exception {
        var key = KeyPairGenerator.getInstance("RSA").generateKeyPair().getPrivate();
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(key.getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
        String credentials = new JsonMapper()
                .toJsonString(Map.of(
                        "type", "service_account",
                        "client_email", "test@example.test",
                        "client_id", "123456789",
                        "private_key", pem,
                        "private_key_id", "fixture",
                        "token_uri", "https://example.test/token"));
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        contextRunner
                .withConfiguration(AutoConfigurations.of(GcpContextAutoConfiguration.class))
                .withPropertyValues(
                        "spring.cloud.gcp.core.enabled=true",
                        "spring.cloud.gcp.project-id=local-test",
                        "spring.cloud.gcp.credentials.encoded-key=" + encoded,
                        "spring.cloud.gcp.credentials.scopes[0]=" + READONLY_SCOPE,
                        "platform-integration.gcp.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(GcpUsersFetcher.class);
                    assertThat(context).hasSingleBean(CredentialsProvider.class);
                    var credential = (ServiceAccountCredentials)
                            context.getBean(CredentialsProvider.class).getCredentials();
                    assertThat(credential.getScopes()).containsExactly(READONLY_SCOPE);
                });
    }

    @Test
    void enabledAzureStarterWiresConfiguredClientSecretAndGraphWithoutNetwork() {
        contextRunner
                .withConfiguration(AutoConfigurations.of(
                        AzureGlobalPropertiesAutoConfiguration.class, AzureTokenCredentialAutoConfiguration.class))
                .withPropertyValues(
                        "platform-integration.azure.enabled=true",
                        "spring.cloud.azure.profile.tenant-id=11111111-1111-1111-1111-111111111111",
                        "spring.cloud.azure.credential.client-id=22222222-2222-2222-2222-222222222222",
                        "spring.cloud.azure.credential.client-secret=local-test-fixture")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(AzureUsersFetcher.class);
                    assertThat(context).hasSingleBean(TokenCredential.class);
                    assertThat(context.getBean(TokenCredential.class)).isInstanceOf(ClientSecretCredential.class);
                });
    }
}
