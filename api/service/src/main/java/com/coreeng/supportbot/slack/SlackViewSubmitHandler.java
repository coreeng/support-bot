package com.coreeng.supportbot.slack;

import com.slack.api.app_backend.views.response.ViewSubmissionResponse;
import com.slack.api.bolt.context.builtin.ViewSubmissionContext;
import com.slack.api.bolt.request.builtin.ViewSubmissionRequest;

import java.util.regex.Pattern;

public interface SlackViewSubmitHandler {
    Pattern getPattern();
    ViewSubmissionResponse apply(ViewSubmissionRequest request, ViewSubmissionContext context);
}
