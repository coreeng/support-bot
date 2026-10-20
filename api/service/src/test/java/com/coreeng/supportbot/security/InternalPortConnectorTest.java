package com.coreeng.supportbot.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class InternalPortConnectorTest {

    @Test
    void addsNoListenerUnlessEnabled() {
        new ApplicationContextRunner()
                .withUserConfiguration(InternalPortConnectorConfig.class)
                .run(context -> assertThat(context).doesNotHaveBean(InternalPortConnectorConfig.class));
        new ApplicationContextRunner()
                .withUserConfiguration(InternalPortConnectorConfig.class)
                .withPropertyValues("internal-port.enabled=false", "internal-port.port=8082")
                .run(context -> assertThat(context).doesNotHaveBean(InternalPortConnectorConfig.class));
    }

    @Nested
    @SpringBootTest(classes = WebApp.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    class WhenEnabled {

        private static final int INTERNAL_PORT = freePort();

        @LocalServerPort
        private int mainPort;

        @DynamicPropertySource
        static void internalPort(DynamicPropertyRegistry registry) {
            registry.add("internal-port.enabled", () -> "true");
            registry.add("internal-port.port", () -> INTERNAL_PORT);
        }

        @Test
        void servesTheApplicationOnTheInternalPortAsWellAsTheMainPort() throws Exception {
            assertThat(localPortSeenBy(INTERNAL_PORT)).isEqualTo(String.valueOf(INTERNAL_PORT));
            assertThat(localPortSeenBy(mainPort)).isEqualTo(String.valueOf(mainPort));
        }

        @Test
        void doesNotAdvertiseTheServerVersionOnTheInternalPort() throws Exception {
            assertThat(get(INTERNAL_PORT).headers().firstValue("Server")).isEmpty();
        }

        private static String localPortSeenBy(int port) throws Exception {
            return get(port).body();
        }

        private static HttpResponse<String> get(int port) throws Exception {
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/local-port"))
                    .GET()
                    .build();
            try (HttpClient client = HttpClient.newHttpClient()) {
                return client.send(request, HttpResponse.BodyHandlers.ofString());
            }
        }
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @SpringBootConfiguration
    @ImportAutoConfiguration({
        ServletWebServerFactoryAutoConfiguration.class,
        DispatcherServletAutoConfiguration.class,
        WebMvcAutoConfiguration.class,
        HttpMessageConvertersAutoConfiguration.class
    })
    @Import({InternalPortConnectorConfig.class, LocalPortController.class})
    static class WebApp {}

    @RestController
    static class LocalPortController {
        @GetMapping("/local-port")
        String localPort(HttpServletRequest request) {
            return String.valueOf(request.getLocalPort());
        }
    }
}
