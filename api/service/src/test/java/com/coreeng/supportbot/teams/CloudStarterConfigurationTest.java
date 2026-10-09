package com.coreeng.supportbot.teams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;
import com.azure.core.http.HttpPipelineCallContext;
import com.azure.core.http.HttpPipelineNextPolicy;
import com.azure.core.http.HttpPipelineNextSyncPolicy;
import com.azure.core.http.HttpResponse;
import com.azure.core.http.policy.HttpPipelinePolicy;
import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.spring.cloud.autoconfigure.implementation.context.AzureGlobalPropertiesAutoConfiguration;
import com.azure.spring.cloud.autoconfigure.implementation.context.AzureTokenCredentialAutoConfiguration;
import com.azure.spring.cloud.core.customizer.AzureServiceClientBuilderCustomizer;
import com.coreeng.supportbot.enums.EscalationTeamsRegistry;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.coreeng.supportbot.teams.groups.GroupRef;
import com.coreeng.supportbot.teams.groups.GroupResolver;
import com.coreeng.supportbot.util.JsonMapper;
import com.google.api.gax.core.CredentialsProvider;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.spring.autoconfigure.core.GcpContextAutoConfiguration;
import com.google.common.collect.ImmutableList;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import reactor.core.publisher.Mono;

class CloudStarterConfigurationTest {
    private static final String READONLY_SCOPE = "https://www.googleapis.com/auth/cloud-identity.groups.readonly";
    private static final String AZURE_TENANT_ID = "11111111-1111-1111-1111-111111111111";
    private static final String AZURE_CLIENT_ID = "22222222-2222-2222-2222-222222222222";
    private static final String AZURE_CLIENT_SECRET = "local-test-fixture";
    private static final String AZURE_GRAPH_TOKEN = "azure-loopback-token";
    private static final String GCP_ACCESS_TOKEN = "gcp-loopback-token";

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
    void enabledGcpStarterExchangesReadonlyCredentialsAndFetchesMemberships() throws Exception {
        var requests = new CopyOnWriteArrayList<CapturedRequest>();
        var unexpectedRequests = new CopyOnWriteArrayList<String>();
        try (var server = LoopbackServer.start(exchange -> {
            var request = capture(exchange);
            requests.add(request);
            if (request.method().equals("POST") && request.path().equals("/token")) {
                respond(exchange, 200, """
                        {"access_token":"%s","token_type":"Bearer","expires_in":3600}
                        """.formatted(GCP_ACCESS_TOKEN));
            } else if (request.method().equals("GET") && request.path().equals("/v1/groups:lookup")) {
                respond(exchange, 200, """
                        {"name":"groups/fixture"}
                        """);
            } else if (request.method().equals("GET")
                    && request.path().equals("/v1/groups/fixture/memberships:searchTransitiveMemberships")) {
                respond(exchange, 200, """
                        {"memberships":[
                          {"member":"users/fixture","preferredMemberKey":[{"id":"member@example.test"}]},
                          {"member":"groups/nested","preferredMemberKey":[{"id":"ignored@example.test"}]}
                        ]}
                        """);
            } else {
                unexpectedRequests.add(request.target());
                respond(exchange, 404, "{}");
            }
        })) {
            String credentials = serviceAccountCredentials(server.url("token"));
            String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
            contextRunner
                    .withConfiguration(AutoConfigurations.of(GcpContextAutoConfiguration.class))
                    .withPropertyValues(
                            "spring.cloud.gcp.core.enabled=true",
                            "spring.cloud.gcp.project-id=local-test",
                            "spring.cloud.gcp.credentials.encoded-key=" + encoded,
                            "spring.cloud.gcp.credentials.scopes[0]=" + READONLY_SCOPE,
                            "platform-integration.gcp.enabled=true",
                            "platform-integration.gcp.client.base-url=" + server.url(""))
                    .run(context -> {
                        assertThat(context).hasNotFailed().hasSingleBean(GcpUsersFetcher.class);
                        assertThat(context).hasSingleBean(CredentialsProvider.class);
                        var credential = (ServiceAccountCredentials)
                                context.getBean(CredentialsProvider.class).getCredentials();
                        assertThat(credential.getScopes()).containsExactly(READONLY_SCOPE);

                        var memberships = context.getBean(GcpUsersFetcher.class)
                                .fetchMembershipsByGroupRef(new GroupRef.Google("fixture@example.test"));
                        assertThat(memberships)
                                .extracting(PlatformUsersFetcher.Membership::email)
                                .containsExactly("member@example.test");
                    });
            assertThat(unexpectedRequests).isEmpty();
            assertThat(requests)
                    .extracting(CapturedRequest::path)
                    .containsExactlyInAnyOrder(
                            "/token",
                            "/v1/groups:lookup",
                            "/v1/groups/fixture/memberships:searchTransitiveMemberships");

            var tokenRequest = requests.stream()
                    .filter(request -> request.path().equals("/token"))
                    .findFirst()
                    .orElseThrow();
            var tokenForm = decodeForm(tokenRequest.body());
            assertThat(tokenForm).containsEntry("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer");
            String jwtAssertion = tokenForm.getOrDefault("assertion", "");
            assertThat(jwtAssertion).isNotEmpty();
            String[] assertion = jwtAssertion.split("\\.");
            assertThat(assertion).hasSize(3);
            String claims = new String(Base64.getUrlDecoder().decode(assertion[1]), StandardCharsets.UTF_8);
            assertThat(claims).contains("\"aud\":\"https://oauth2.googleapis.com/token\"", READONLY_SCOPE);

            var cloudIdentityRequests = requests.stream()
                    .filter(request -> request.path().startsWith("/v1/"))
                    .toList();
            assertThat(cloudIdentityRequests)
                    .allSatisfy(request -> assertThat(request.authorization()).isEqualTo("Bearer " + GCP_ACCESS_TOKEN));
            assertThat(cloudIdentityRequests.stream()
                            .filter(request -> request.path().equals("/v1/groups:lookup"))
                            .findFirst()
                            .orElseThrow()
                            .target())
                    .satisfies(target -> assertThat(URLDecoder.decode(target, StandardCharsets.UTF_8))
                            .contains("groupKey.id=fixture@example.test"));
        }
    }

    @Test
    void enabledAzureStarterExchangesClientCredentialsAndFetchesGraphMemberships() throws Exception {
        var requests = new CopyOnWriteArrayList<CapturedRequest>();
        var unexpectedRequests = new CopyOnWriteArrayList<String>();
        try (var server = LoopbackServer.start(exchange -> {
            var request = capture(exchange);
            requests.add(request);
            if (request.method().equals("POST")
                    && request.path().equals("/" + AZURE_TENANT_ID + "/oauth2/v2.0/token")) {
                respond(exchange, 200, """
                        {"access_token":"%s","token_type":"Bearer","expires_in":3600,"scope":"https://graph.microsoft.com/.default"}
                        """.formatted(AZURE_GRAPH_TOKEN));
            } else if (request.method().equals("GET") && request.path().startsWith("/v1.0/")) {
                respond(exchange, 200, """
                        {
                          "@odata.context":"https://graph.microsoft.com/v1.0/$metadata#users",
                          "value":[
                            {"@odata.type":"#microsoft.graph.user","id":"mail-user","mail":"member@example.test","accountEnabled":true},
                            {"@odata.type":"#microsoft.graph.user","id":"upn-user","mail":null,"userPrincipalName":"fallback@example.test","accountEnabled":true},
                            {"@odata.type":"#microsoft.graph.user","id":"disabled-user","mail":"disabled@example.test","accountEnabled":false}
                          ]
                        }
                        """);
            } else {
                unexpectedRequests.add(request.target());
                respond(exchange, 404, "{}");
            }
        })) {
            contextRunner
                    .withUserConfiguration(AzureCredentialTestConfiguration.class)
                    .withBean(LoopbackServer.class, () -> server)
                    .withConfiguration(AutoConfigurations.of(
                            AzureGlobalPropertiesAutoConfiguration.class, AzureTokenCredentialAutoConfiguration.class))
                    .withPropertyValues(
                            "platform-integration.azure.enabled=true",
                            "platform-integration.azure.client.base-url=" + server.url("v1.0"),
                            "spring.cloud.azure.profile.tenant-id=" + AZURE_TENANT_ID,
                            "spring.cloud.azure.credential.client-id=" + AZURE_CLIENT_ID,
                            "spring.cloud.azure.credential.client-secret=" + AZURE_CLIENT_SECRET)
                    .run(context -> {
                        assertThat(context).hasNotFailed().hasSingleBean(AzureUsersFetcher.class);
                        assertThat(context).hasSingleBean(TokenCredential.class);
                        var credential = context.getBean(TokenCredential.class);
                        assertThat(credential).isInstanceOf(ClientSecretCredential.class);

                        // Graph Core intentionally limits the production provider to cloud Graph hosts. Exercise
                        // the real Azure credential against loopback separately, then test the production fetcher
                        // without pretending its cloud-host allowlist authorizes a local URL.
                        var token = credential.getTokenSync(
                                new TokenRequestContext().addScopes("https://graph.microsoft.com/.default"));
                        assertThat(token.getToken()).isEqualTo(AZURE_GRAPH_TOKEN);

                        List<PlatformUsersFetcher.Membership> memberships;
                        try {
                            memberships = context.getBean(AzureUsersFetcher.class)
                                    .fetchMembershipsByGroupRef(new GroupRef.Azure("fixture-group"));
                        } catch (RuntimeException e) {
                            String paths = requests.stream()
                                    .map(request -> request.method() + " " + request.target())
                                    .collect(Collectors.joining(", "));
                            throw new AssertionError("Azure loopback request paths: " + paths, e);
                        }
                        assertThat(memberships)
                                .extracting(PlatformUsersFetcher.Membership::email)
                                .containsExactly("member@example.test", "fallback@example.test");
                    });

            assertThat(unexpectedRequests).isEmpty();
            assertThat(requests)
                    .extracting(CapturedRequest::path)
                    .containsExactlyInAnyOrder(
                            "/" + AZURE_TENANT_ID + "/oauth2/v2.0/token",
                            "/v1.0/groups/fixture-group/transitiveMembers/graph.user");
            var tokenRequest = requests.stream()
                    .filter(request -> request.path().endsWith("/oauth2/v2.0/token"))
                    .findFirst()
                    .orElseThrow();
            var tokenForm = decodeForm(tokenRequest.body());
            assertThat(tokenForm)
                    .containsEntry("grant_type", "client_credentials")
                    .containsEntry("client_id", AZURE_CLIENT_ID)
                    .containsEntry("client_secret", AZURE_CLIENT_SECRET)
                    .containsKey("scope");
            assertThat(tokenForm.getOrDefault("scope", "").split(" ")).contains("https://graph.microsoft.com/.default");

            var graphRequest = requests.stream()
                    .filter(request -> request.path().startsWith("/v1.0/"))
                    .findFirst()
                    .orElseThrow();
            assertThat(graphRequest.authorization()).isNull();
            assertThat(URLDecoder.decode(graphRequest.target(), StandardCharsets.UTF_8))
                    .contains("$select=mail,accountEnabled,deletedDateTime,userPrincipalName");
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class AzureCredentialTestConfiguration {
        @Bean
        AzureServiceClientBuilderCustomizer<ClientSecretCredentialBuilder> localOnlyAzureCredentialBuilderCustomizer(
                LoopbackServer loopbackServer) {
            return builder -> {
                builder.disableInstanceDiscovery();
                builder.proxyOptions(null);
                builder.addPolicy(new LoopbackAzureAuthPolicy(loopbackServer));
            };
        }
    }

    private static String serviceAccountCredentials(String tokenUri) throws Exception {
        var key = KeyPairGenerator.getInstance("RSA").generateKeyPair().getPrivate();
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(key.getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
        return new JsonMapper()
                .toJsonString(Map.of(
                        "type", "service_account",
                        "client_email", "test@example.test",
                        "client_id", "123456789",
                        "private_key", pem,
                        "private_key_id", "fixture",
                        "token_uri", tokenUri));
    }

    private static CapturedRequest capture(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        return new CapturedRequest(
                exchange.getRequestMethod(),
                exchange.getRequestURI().getPath(),
                exchange.getRequestURI().toString(),
                exchange.getRequestHeaders().getFirst("Authorization"),
                exchange.getRequestHeaders().getFirst("Content-Type"),
                body);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var responseBody = exchange.getResponseBody()) {
            responseBody.write(bytes);
        }
    }

    private static Map<String, String> decodeForm(String form) {
        return Arrays.stream(form.split("&"))
                .map(pair -> pair.split("=", 2))
                .collect(Collectors.toMap(
                        pair -> URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                        pair -> URLDecoder.decode(pair.length == 1 ? "" : pair[1], StandardCharsets.UTF_8)));
    }

    private record CapturedRequest(
            String method, String path, String target, String authorization, String contentType, String body) {}

    private static final class LoopbackServer implements AutoCloseable {
        private final HttpServer server;

        private LoopbackServer(HttpServer server) {
            this.server = server;
        }

        static LoopbackServer start(HttpHandler handler) throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/", handler);
            server.start();
            return new LoopbackServer(server);
        }

        int port() {
            return server.getAddress().getPort();
        }

        String url(String path) {
            return localUri("/" + path).toASCIIString();
        }

        private URI localUri(String pathAndQuery) {
            String host = server.getAddress().getAddress().getHostAddress();
            String authority = host.contains(":") ? "[" + host + "]:" + port() : host + ":" + port();
            return URI.create("http://" + authority + pathAndQuery);
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

    private static final class LoopbackAzureAuthPolicy implements HttpPipelinePolicy {
        private final LoopbackServer loopbackServer;

        private LoopbackAzureAuthPolicy(LoopbackServer loopbackServer) {
            this.loopbackServer = loopbackServer;
        }

        @Override
        public Mono<HttpResponse> process(HttpPipelineCallContext context, HttpPipelineNextPolicy next) {
            try {
                routeToLoopback(context);
                return next.process();
            } catch (RuntimeException e) {
                return Mono.error(e);
            }
        }

        @Override
        public HttpResponse processSync(HttpPipelineCallContext context, HttpPipelineNextSyncPolicy next) {
            routeToLoopback(context);
            return next.processSync();
        }

        private void routeToLoopback(HttpPipelineCallContext context) {
            URL destination = context.getHttpRequest().getUrl();
            if (!destination.getProtocol().equals("https")
                    || !destination.getHost().equalsIgnoreCase("login.microsoftonline.com")) {
                throw new IllegalStateException("Unexpected Azure authentication destination: " + destination);
            }
            try {
                URI loopback = loopbackServer.localUri(destination.getFile());
                context.getHttpRequest().setUrl(loopback.toURL());
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException("Could not route Azure authentication request to loopback", e);
            }
        }
    }
}
