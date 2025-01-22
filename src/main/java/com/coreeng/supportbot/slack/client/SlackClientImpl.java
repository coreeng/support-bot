package com.coreeng.supportbot.slack.client;

import com.coreeng.supportbot.slack.SlackException;
import com.google.common.collect.ImmutableList;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.SlackApiTextResponse;
import com.slack.api.methods.request.chat.ChatGetPermalinkRequest;
import com.slack.api.methods.request.conversations.ConversationsHistoryRequest;
import com.slack.api.methods.request.reactions.ReactionsAddRequest;
import com.slack.api.methods.request.reactions.ReactionsRemoveRequest;
import com.slack.api.methods.request.users.profile.UsersProfileGetRequest;
import com.slack.api.methods.request.views.ViewsOpenRequest;
import com.slack.api.methods.request.views.ViewsPublishRequest;
import com.slack.api.methods.response.chat.ChatGetPermalinkResponse;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.methods.response.chat.ChatUpdateResponse;
import com.slack.api.methods.response.conversations.ConversationsHistoryResponse;
import com.slack.api.methods.response.reactions.ReactionsAddResponse;
import com.slack.api.methods.response.reactions.ReactionsRemoveResponse;
import com.slack.api.methods.response.views.ViewsOpenResponse;
import com.slack.api.methods.response.views.ViewsPublishResponse;
import com.slack.api.model.ErrorResponseMetadata;
import com.slack.api.model.Message;
import com.slack.api.model.ResponseMetadata;
import com.slack.api.model.User;
import com.slack.api.model.view.View;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.function.Function;

import static com.google.common.collect.Iterables.isEmpty;

@RequiredArgsConstructor
public class SlackClientImpl implements SlackClient {
    private final MethodsClient client;
    private final Cache cache;

    @Override
    public ReactionsAddResponse addReaction(ReactionsAddRequest request) {
        return doRequest(() -> client.reactionsAdd(request), null);
    }

    @Override
    public ReactionsRemoveResponse removeReaction(ReactionsRemoveRequest request) {
        return doRequest(() -> client.reactionsRemove(request), null);
    }

    @Override
    public ChatPostMessageResponse postMessage(SlackPostMessageRequest request) {
        return doRequest(
            () -> client.chatPostMessage(request.toSlackRequest()),
            response -> ImmutableList.<String>builder()
                .addAll(errorDetailsOrEmpty(response.getResponseMetadata()))
                .addAll(response.getErrors())
                .build()
        );
    }

    @Override
    public ChatUpdateResponse editMessage(SlackEditMessageRequest request) {
        return doRequest(
            () -> client.chatUpdate(request.toSlackRequest()),
            response -> errorDetailsOrEmpty(response.getResponseMetadata())
        );
    }

    @Override
    public Message getMessageByTs(SlackGetMessageByTsRequest request) {
        ConversationsHistoryResponse response = doRequest(() -> client.conversationsHistory(ConversationsHistoryRequest.builder()
                .channel(request.channelId())
                .oldest(request.ts().ts())
                .limit(1)
                .inclusive(true)
                .build()),
            r -> errorDetailsOrEmpty(r.getResponseMetadata()));
        return response.getMessages().getFirst();
    }

    @Override
    public String getPermalink(SlackGetMessageByTsRequest request) {
        return cache.get(request, () -> {
            ChatGetPermalinkResponse response = doRequest(() -> client.chatGetPermalink(ChatGetPermalinkRequest.builder()
                .channel(request.channelId())
                .messageTs(request.ts().ts())
                .build()), null);
            return response.getPermalink();
        });
    }

    @Override
    public ViewsOpenResponse viewsOpen(ViewsOpenRequest request) {
        return doRequest(
            () -> client.viewsOpen(request),
            response -> errorDetailsOrEmpty(response.getResponseMetadata())
        );
    }

    @Override
    public ViewsPublishResponse updateHomeView(String userId, SlackView view) {
        return doRequest(
            () -> client.viewsPublish(ViewsPublishRequest.builder()
                .userId(userId)
                .view(View.builder()
                    .type("home")
                    .blocks(view.renderBlocks())
                    .privateMetadata(view.privateMetadata())
                    .build())
                .build()),
            response -> errorDetailsOrEmpty(response.getResponseMetadata())
        );
    }

    @Override
    public User.Profile getUserById(String userId) {
        return doRequest(
            () -> client.usersProfileGet(UsersProfileGetRequest.builder()
                .user(userId)
                .build()),
            null
        ).getProfile();
    }

    private <V extends SlackApiTextResponse> V doRequest(
        SlackRequestCallable<V> doRequest,
        @Nullable Function<V, ImmutableList<String>> errorMetadataExtractor
    ) {
        try {
            V response = doRequest.call();
            if (!response.isOk()) {
                throw new SlackException(
                    response,
                    errorMetadataExtractor != null
                        ? errorMetadataExtractor.apply(response)
                        : ImmutableList.of()
                );
            }
            return response;
        } catch (IOException | SlackApiException e) {
            throw new SlackException(e);
        }
    }

    private ImmutableList<String> errorDetailsOrEmpty(ErrorResponseMetadata metadata) {
        if (metadata != null && !isEmpty(metadata.getMessages())) {
            return ImmutableList.copyOf(metadata.getMessages());
        } else {
            return ImmutableList.of();
        }
    }


    private ImmutableList<String> errorDetailsOrEmpty(ResponseMetadata metadata) {
        if (metadata != null && !isEmpty(metadata.getMessages())) {
            return ImmutableList.copyOf(metadata.getMessages());
        } else {
            return ImmutableList.of();
        }
    }

    @FunctionalInterface
    private interface SlackRequestCallable<V extends SlackApiTextResponse> {
        V call() throws IOException, SlackApiException;
    }
}
