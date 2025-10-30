package com.coreeng.supportbot.slack.client;

import com.google.common.collect.ImmutableList;
import com.slack.api.methods.request.conversations.ConversationsRepliesRequest;
import com.slack.api.methods.request.reactions.ReactionsAddRequest;
import com.slack.api.methods.request.reactions.ReactionsRemoveRequest;
import com.slack.api.methods.request.views.ViewsOpenRequest;
import com.slack.api.methods.response.chat.ChatPostEphemeralResponse;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.methods.response.chat.ChatUpdateResponse;
import com.slack.api.methods.response.conversations.ConversationsRepliesResponse;
import com.slack.api.methods.response.reactions.ReactionsAddResponse;
import com.slack.api.methods.response.reactions.ReactionsRemoveResponse;
import com.slack.api.methods.response.views.ViewsOpenResponse;
import com.slack.api.methods.response.views.ViewsPublishResponse;
import com.slack.api.model.Message;
import com.slack.api.model.User;

import java.util.List;

public interface SlackClient {
    ReactionsAddResponse addReaction(ReactionsAddRequest request);

    ReactionsRemoveResponse removeReaction(ReactionsRemoveRequest request);

    ChatPostMessageResponse postMessage(SlackPostMessageRequest request);

    ChatPostEphemeralResponse postEphemeralMessage(SlackPostEphemeralMessageRequest request);

    ChatUpdateResponse editMessage(SlackEditMessageRequest request);

    Message getMessageByTs(SlackGetMessageByTsRequest request);

    String getPermalink(SlackGetMessageByTsRequest request);

    ConversationsRepliesResponse getThreadPage(ConversationsRepliesRequest request);

    ViewsOpenResponse viewsOpen(ViewsOpenRequest request);

    ViewsPublishResponse updateHomeView(String userId, SlackView view);

    User.Profile getUserById(String userId);

    ImmutableList<String> getGroupMembers(String groupId);
}
