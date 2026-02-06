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
import com.slack.api.methods.request.conversations.ConversationsOpenRequest;
import com.slack.api.methods.request.conversations.ConversationsRepliesRequest;
import com.slack.api.methods.request.reactions.ReactionsAddRequest;
import com.slack.api.methods.request.reactions.ReactionsRemoveRequest;
import com.slack.api.methods.request.conversations.ConversationsInfoRequest;
import com.slack.api.methods.request.usergroups.UsergroupsListRequest;
import com.slack.api.methods.request.usergroups.users.UsergroupsUsersListRequest;
import com.slack.api.methods.request.users.UsersInfoRequest;
import com.slack.api.methods.request.views.ViewsOpenRequest;
import com.slack.api.methods.request.views.ViewsPublishRequest;
import com.slack.api.methods.response.chat.ChatGetPermalinkResponse;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.methods.response.chat.ChatUpdateResponse;
import com.slack.api.methods.response.conversations.ConversationsHistoryResponse;
import com.slack.api.methods.response.conversations.ConversationsOpenResponse;
import com.slack.api.methods.response.conversations.ConversationsRepliesResponse;
import com.slack.api.methods.response.usergroups.UsergroupsListResponse;
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
import lombok.extern.slf4j.Slf4j;

import org.springframework.cache.Cache;

import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import static com.google.common.collect.Iterables.isEmpty;
import com.slack.api.methods.response.chat.ChatPostEphemeralResponse;

@RequiredArgsConstructor
@Slf4j
public class SlackClientImpl implements SlackClient {
    private final MethodsClient client;
    private final Cache permalinkCache;
    private final Cache userProfileCache;
    private final Cache groupCache;
    private final Cache channelCache;
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
                .addAll(response.getErrors() == null ? List.of() : response.getErrors())
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
        return Objects.requireNonNull(permalinkCache.get(request, () -> {
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
        }));
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
    public User getUserById(SlackId.User userId) {
        return Objects.requireNonNull(userProfileCache.get(userId.id(), () -> doRequest(
            "users.info",
            () -> client.usersInfo(UsersInfoRequest.builder()
                .user(userId.id())
                .build()),
            null
        ).getUser()));
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

    @Override
    @Nullable
    public String getGroupName(SlackId.Group groupId) {
        Cache.ValueWrapper wrapper = groupCache.get(groupId.id());
        if (wrapper != null) {
            return (String) wrapper.get();
        }
        try {
            UsergroupsListResponse response = doRequest(
                "usergroups.list",
                () -> client.usergroupsList(UsergroupsListRequest.builder().build()),
                null
            );
            String handle = response.getUsergroups().stream()
                .filter(ug -> groupId.id().equals(ug.getId()))
                .map(com.slack.api.model.Usergroup::getHandle)
                .findFirst()
                .orElse(null);
            if (handle != null) {
                groupCache.put(groupId.id(), handle);
            }
            return handle;
        } catch (SlackException e) {
            if (log.isDebugEnabled()) {
                log.debug("Failed to resolve group {}: {}", groupId.id(), e.getMessage());
            }
            return null;
        }
    }

    @Override
    @Nullable
    public String getChannelName(String channelId) {
        Cache.ValueWrapper wrapper = channelCache.get(channelId);
        if (wrapper != null) {
            return (String) wrapper.get();
        }
        try {
            var response = doRequest(
                "conversations.info",
                () -> client.conversationsInfo(ConversationsInfoRequest.builder()
                    .channel(channelId)
                    .build()),
                null
            );
            String name = response.getChannel().getName();
            channelCache.put(channelId, name);
            return name;
        } catch (SlackException e) {
            if (log.isDebugEnabled()) {
                log.debug("Failed to resolve channel {}: {}", channelId, e.getMessage());
            }
            return null;
        }
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

    @Override
    public ConversationsOpenResponse openDmConversation(SlackId.User userId) {
        return doRequest(
            "conversations.open",
            () -> client.conversationsOpen(ConversationsOpenRequest.builder()
                .users(ImmutableList.of(userId.id()))
                .build()),
            response -> ImmutableList.of(response.getError())
        );
    }

    @FunctionalInterface
    private interface SlackRequestCallable<V extends SlackApiTextResponse> {
        V call() throws IOException, SlackApiException;
    }
}
