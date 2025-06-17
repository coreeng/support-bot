package com.coreeng.supportbot.config;

import com.slack.api.bolt.App;
import com.slack.api.bolt.jakarta_socket_mode.SocketModeApp;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.CommandLineRunner;

import java.io.IOException;

public class SlackSocketController implements CommandLineRunner {
    private final SocketModeApp socketModeApp;

    public SlackSocketController(
        App app,
        SlackProps slackCreds
    ) throws IOException {
        this.socketModeApp = new SocketModeApp(
            slackCreds.creds().socketToken(),
            app,
            10
        );
    }

    @Override
    public void run(String... args) throws Exception {
        socketModeApp.startAsync();
    }

    @PreDestroy
    public void destroy() throws Exception { //NOPMD - suppressed SignatureDeclareThrowsException - it's thrown by the underlying library
        socketModeApp.close();
    }
}
