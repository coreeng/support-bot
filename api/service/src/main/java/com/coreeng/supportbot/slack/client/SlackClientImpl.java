package com.coreeng.supportbot.slack.client;

import com.coreeng.supportbot.slack.SlackException;
import com.coreeng.supportbot.slack.SlackId;
import com.google.common.collect.ImmutableList;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.SlackApiTextResponse;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import com.slack.api.methods.request.chat.ChatGetPermalinkRequest;
import com.slack.api.methods.request.conversations.ConversationsHistoryRequest;
import com.slack.api.methods.request.conversations.ConversationsRepliesRequest;
import com.slack.api.methods.request.reactions.ReactionsAddRequest;
import com.slack.api.methods.request.reactions.ReactionsRemoveRequest;
import com.slack.api.methods.request.usergroups.users.UsergroupsUsersListRequest;
import com.slack.api.methods.request.users.profile.UsersProfileGetRequest;
import com.slack.api.methods.request.views.ViewsOpenRequest;
import com.slack.api.methods.request.views.ViewsPublishRequest;
import com.slack.api.methods.response.chat.ChatGetPermalinkResponse;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.methods.response.chat.ChatUpdateResponse;
import com.slack.api.methods.response.conversations.ConversationsHistoryResponse;
import com.slack.api.methods.response.conversations.ConversationsRepliesResponse;
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
import java.util.List;
import java.util.function.Function;

import static com.google.common.collect.Iterables.isEmpty;
import com.slack.api.methods.response.chat.ChatPostEphemeralResponse;

@RequiredArgsConstructor
public class SlackClientImpl implements SlackClient {
    private final MethodsClient client;
    private final Cache permalinkCache;
    private final Cache userProfileCache;
    private final MeterRegistry meterRegistry;

    @Override
    public ReactionsAddResponse addReaction(ReactionsAddRequest request) {
        return doRequest("reactions.add", () -> client.reactionsAdd(request), null);
    }

    @Override
    public ReactionsRemoveResponse removeReaction(ReactionsRemoveRequest request) {
        return doRequest("reactions.remove", () -> client.reactionsRemove(request), null);
    }

    @Override
    public ChatPostMessageResponse postMessage(SlackPostMessageRequest request) {
        return doRequest(
            "chat.postMessage",
            () -> client.chatPostMessage(request.toSlackRequest()),
            response -> ImmutableList.<String>builder()
                .addAll(errorDetailsOrEmpty(response.getResponseMetadata()))
                .addAll(response.getErrors())
                .build()
        );
    }

    @Override
    public ChatPostEphemeralResponse postEphemeralMessage(SlackPostEphemeralMessageRequest request) {
        return doRequest(
            "chat.postEphemeral",
            () -> client.chatPostEphemeral(request.toSlackRequest()),
            response -> ImmutableList.of(response.getError())
        );
    }

    @Override
    public ChatUpdateResponse editMessage(SlackEditMessageRequest request) {
        return doRequest(
            "chat.update",
            () -> client.chatUpdate(request.toSlackRequest()),
            response -> errorDetailsOrEmpty(response.getResponseMetadata())
        );
    }

    @Override
    public Message getMessageByTs(SlackGetMessageByTsRequest request) {
        ConversationsHistoryResponse response = doRequest(
            "conversations.history",
            () -> client.conversationsHistory(ConversationsHistoryRequest.builder()
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
        return permalinkCache.get(request, () -> {
            if (request.ts().mocked()) {
                return "https://slack.com/" + request.channelId() + "/" + request.ts().ts();
            }
            ChatGetPermalinkResponse response = doRequest(
                "chat.getPermalink",
                () -> client.chatGetPermalink(ChatGetPermalinkRequest.builder()
                    .channel(request.channelId())
                    .messageTs(request.ts().ts())
                    .build()),
                null);
            return response.getPermalink();
        });
    }

    @Override
    public ConversationsRepliesResponse getThreadPage(ConversationsRepliesRequest request) {
        return doRequest(
            "conversations.replies",
            () -> client.conversationsReplies(request),
            response -> errorDetailsOrEmpty(response.getResponseMetadata())
        );
    }

    @Override
    public ViewsOpenResponse viewsOpen(ViewsOpenRequest request) {
        return doRequest(
            "views.open",
            () -> client.viewsOpen(request),
            response -> errorDetailsOrEmpty(response.getResponseMetadata())
        );
    }

    @Override
    public ViewsPublishResponse updateHomeView(SlackId.User userId, SlackView view) {
        return doRequest(
            "views.publish",
            () -> client.viewsPublish(ViewsPublishRequest.builder()
                .userId(userId.id())
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
    public User.Profile getUserById(SlackId.User userId) {
        return userProfileCache.get(userId.id(), () -> doRequest(
            "users.profile.get",
            () -> client.usersProfileGet(UsersProfileGetRequest.builder()
                .user(userId.id())
                .build()),
            null
        ).getProfile());
    }

    @Override
    public ImmutableList<String> getGroupMembers(SlackId.Group groupId) {
        List<String> users = doRequest(
            "usergroups.users.list",
            () -> client.usergroupsUsersList(UsergroupsUsersListRequest.builder()
                .usergroup(groupId.id())
                .build()),
            null
        ).getUsers();
        return ImmutableList.copyOf(users);
    }

    private <V extends SlackApiTextResponse> V doRequest(
        String methodName,
        SlackRequestCallable<V> doRequest,
        @Nullable Function<V, ImmutableList<String>> errorMetadataExtractor
    ) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            V response = doRequest.call();
            if (!response.isOk()) {
                meterRegistry.counter("slack_api_calls_errors_total",
                    "method", methodName,
                    "error_code", response.getError() != null ? response.getError() : "unknown"
                ).increment();
                throw new SlackException(
                    response,
                    errorMetadataExtractor != null
                        ? errorMetadataExtractor.apply(response)
                        : ImmutableList.of()
                );
            }
            meterRegistry.counter("slack_api_calls_success_total", "method", methodName).increment();
            return response;
        } catch (IOException | SlackApiException e) {
            meterRegistry.counter("slack_api_calls_errors_total",
                "method", methodName,
                "error_code", e.getClass().getSimpleName()
            ).increment();
            throw new SlackException(e);
        } finally {
            sample.stop(Timer.builder("slack_api_calls_duration_seconds")
                .tag("method", methodName)
                .publishPercentileHistogram()
                .register(meterRegistry));
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
