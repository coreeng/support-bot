package com.coreeng.supportbot.testkit;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

public record Config(
        Mocks mocks,
        List<Tenant> tenants,
        List<User> users,
        List<EscalationTeam> escalationTeams,
        List<Tag> tags,
        List<Impact> impacts,
        SupportBot supportBot) {
    public List<User> nonSupportUsers() {
        return users.stream()
                .filter(u -> mocks.slack.supportMembers.stream().noneMatch(sm -> sm.email.equals(u.email)))
                .toList();
    }

    public List<User> supportUsers() {
        return users.stream()
                .filter(u -> mocks.slack.supportMembers.stream().anyMatch(sm -> sm.email.equals(u.email)))
                .toList();
    }

    /**
     * Loads configuration from a YAML file on the classpath.
     *
     * @param classpathResource the classpath resource path (e.g., "config.yaml")
     * @return the loaded Config instance
     */
    public static Config load(String classpathResource) {
        try {
            var env = new StandardEnvironment();
            var yamlLoader = new YamlPropertySourceLoader();
            List<PropertySource<?>> yaml = yamlLoader.load("config", new ClassPathResource(classpathResource));
            yaml.forEach(ps -> env.getPropertySources().addLast(ps));
            var binder =
                    new Binder(ConfigurationPropertySources.get(env), new PropertySourcesPlaceholdersResolver(env));
            return binder.bind("", Config.class).get();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load config from " + classpathResource, e);
        }
    }

    public record Mocks(SlackMock slack, KubernetesMock kubernetes, AzureMock azure, GCPMock gcp) {}

    public record SlackMock(
            int port,
            String serverUrl,
            String team,
            String teamId,
            String supportBotUserId,
            String supportBotId,
            String supportGroupId,
            String supportChannelId,
            List<SlackSupportMember> supportMembers,
            @Nullable String mode,
            @Nullable String remoteAdminScheme,
            @Nullable String remoteAdminHost,
            @Nullable Integer remoteAdminPort) {
        public WireMockMode wireMockMode() {
            if (mode == null || mode.isBlank()) {
                return WireMockMode.EMBEDDED;
            }
            return WireMockMode.valueOf(mode.trim().toUpperCase(Locale.ROOT));
        }

        public String remoteAdminSchemeOrDefault() {
            if (remoteAdminScheme == null || remoteAdminScheme.isBlank()) {
                return "http";
            }
            return remoteAdminScheme;
        }

        public String remoteAdminHostOrDefault() {
            if (remoteAdminHost == null || remoteAdminHost.isBlank()) {
                return "localhost";
            }
            return remoteAdminHost;
        }

        public int remoteAdminPortOrDefault() {
            return remoteAdminPort != null ? remoteAdminPort : port;
        }

        public String adminBaseUrl() {
            if (wireMockMode() == WireMockMode.REMOTE) {
                return "%s://%s:%d"
                        .formatted(
                                remoteAdminSchemeOrDefault(), remoteAdminHostOrDefault(), remoteAdminPortOrDefault());
            }
            return "http://localhost:%d".formatted(port);
        }
    }

    public record SlackSupportMember(String userId, String name, String email) {}

    public record KubernetesMock(int port) {}

    public record AzureMock(int port) {}

    public record GCPMock(int port) {}

    public record Tenant(String name, String groupRef, List<User> users) {}

    public record User(
            @Nullable String email,
            @Nullable String slackUserId,
            @Nullable String slackBotId) {
        public boolean isHuman() {
            return email != null && slackUserId != null;
        }

        public boolean isBot() {
            return slackBotId != null;
        }
    }

    public record SupportBot(String baseUrl, String token) {}

    public record EscalationTeam(String label, String code, String slackGroupId) {}

    public record Tag(String code, String label) {}

    public record Impact(String code, String label) {}
}
