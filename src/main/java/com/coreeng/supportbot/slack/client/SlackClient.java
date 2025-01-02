package com.coreeng.supportbot.slack.client;

import com.slack.api.methods.request.reactions.ReactionsAddRequest;
import com.slack.api.methods.request.reactions.ReactionsRemoveRequest;
import com.slack.api.methods.request.views.ViewsOpenRequest;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.methods.response.chat.ChatUpdateResponse;
import com.slack.api.methods.response.reactions.ReactionsAddResponse;
import com.slack.api.methods.response.reactions.ReactionsRemoveResponse;
import com.slack.api.methods.response.views.ViewsOpenResponse;
import com.slack.api.model.Message;

public interface SlackClient {
    ReactionsAddResponse addReaction(ReactionsAddRequest request);

    ReactionsRemoveResponse removeReaction(ReactionsRemoveRequest request);

    ChatPostMessageResponse postMessage(SlackPostMessageRequest request);

    ChatUpdateResponse editMessage(SlackEditMessageRequest request);

    Message getMessageByTs(SlackGetMessageByTsRequest request);

    String getPermalink(SlackGetMessageByTsRequest request);

    ViewsOpenResponse viewsOpen(ViewsOpenRequest request);
}
