package com.coreeng.supportbot.config;

import com.slack.api.bolt.App;
import com.slack.api.bolt.jakarta_socket_mode.SocketModeApp;
import com.slack.api.socket_mode.SocketModeClient;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class SlackSocketController implements CommandLineRunner {
    private final SocketModeApp socketModeApp;
    private final Counter disconnectCounter;
    private final Counter errorCounter;

    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    public SlackSocketController(
        App app,
        SlackProps slackCreds,
        MeterRegistry meterRegistry
    ) throws IOException {
        this.socketModeApp = new SocketModeApp(
            slackCreds.creds().socketToken(),
            app,
            10
        );

        this.disconnectCounter = Counter.builder("slack_socket_disconnects_total")
            .description("Total number of WebSocket disconnections")
            .register(meterRegistry);

        this.errorCounter = Counter.builder("slack_socket_errors_total")
            .description("Total number of WebSocket errors")
            .register(meterRegistry);
    }

    @Override
    public void run(String... args) throws Exception {
        socketModeApp.startAsync();

        SocketModeClient client = socketModeApp.getClient();
        if (client != null) {
            client.addWebSocketCloseListener((code, reason) -> {
                if (!shuttingDown.get()) {
                    log.warn("WebSocket connection closed (code: {}, reason: {})", code, reason);
                    disconnectCounter.increment();
                }
            });

            client.addWebSocketErrorListener(error -> {
                log.error("WebSocket error occurred", error);
                errorCounter.increment();
            });
        }
    }

    @PreDestroy
    public void destroy() throws Exception { //NOPMD - suppressed SignatureDeclareThrowsException - it's thrown by the underlying library
        shuttingDown.set(true);
        socketModeApp.close();
    }
}
