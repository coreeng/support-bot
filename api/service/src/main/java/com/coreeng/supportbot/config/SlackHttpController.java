package com.coreeng.supportbot.config;

import com.slack.api.bolt.App;
import com.slack.api.bolt.jakarta_servlet.SlackAppServlet;

public class SlackHttpController extends SlackAppServlet {
    public SlackHttpController(App app) {
        super(app);
    }
}
