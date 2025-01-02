package com.coreeng.supportbot.config;

import com.slack.api.bolt.App;
import com.slack.api.bolt.jakarta_socket_mode.SocketModeApp;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Profile("!test")
public class SlackSocketController implements ApplicationRunner {
    private final SocketModeApp socketModeApp;

    public SlackSocketController(
        App app,
        SlackCredsProps slackCreds
    ) throws IOException {
        this.socketModeApp = new SocketModeApp(
            slackCreds.socketToken(),
            app
        );
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        socketModeApp.start();
    }

    @PreDestroy
    public void destroy() throws Exception { //NOPMD - suppressed SignatureDeclareThrowsException - it's thrown by the underlying library
        socketModeApp.close();
    }
}
