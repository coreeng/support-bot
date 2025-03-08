package com.coreeng.supportbot.slack;

import com.slack.api.bolt.context.builtin.ActionContext;
import com.slack.api.bolt.request.builtin.BlockActionRequest;
import com.slack.api.methods.SlackApiException;

import java.io.IOException;
import java.util.regex.Pattern;

public interface SlackBlockActionHandler {
    Pattern getPattern();

    void apply(BlockActionRequest req, ActionContext context) throws IOException, SlackApiException;
}
