package com.coreeng.supportbot.slack.client;

import com.coreeng.supportbot.slack.SlackId;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.request.conversations.ConversationsInfoRequest;
import com.slack.api.methods.request.usergroups.UsergroupsListRequest;
import com.slack.api.methods.response.conversations.ConversationsInfoResponse;
import com.slack.api.methods.response.usergroups.UsergroupsListResponse;
import com.slack.api.model.Usergroup;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SlackClientImplTest {

    private MethodsClient methodsClient;
    private Cache permalinkCache;
    private Cache userCache;
    private Cache groupCache;
    private Cache channelCache;
    private SimpleMeterRegistry meterRegistry;
    private SlackClientImpl slackClient;

    @BeforeEach
    void setUp() {
        methodsClient = mock(MethodsClient.class);
        permalinkCache = new ConcurrentMapCache("permalink");
        userCache = new ConcurrentMapCache("user");
        groupCache = new ConcurrentMapCache("group");
        channelCache = new ConcurrentMapCache("channel");
        meterRegistry = new SimpleMeterRegistry();
        slackClient = new SlackClientImpl(methodsClient, permalinkCache, userCache, groupCache, channelCache, meterRegistry);
    }

    @Test
    void getGroupNameUsesCache() {
        // Given
        groupCache.put("S1", "cached-handle");

        // When
        String result = slackClient.getGroupName(SlackId.group("S1"));

        // Then
        assertThat(result).isEqualTo("cached-handle");
        verifyNoInteractions(methodsClient);
    }

    @Test
    void getGroupNameFetchesAndCaches() throws Exception {
        // Given
        Usergroup ug = new Usergroup();
        ug.setId("S1");
        ug.setHandle("handle-1");
        UsergroupsListResponse response = new UsergroupsListResponse();
        response.setOk(true);
        response.setUsergroups(java.util.List.of(ug));
        when(methodsClient.usergroupsList(any(UsergroupsListRequest.class))).thenReturn(response);

        // When
        String result1 = slackClient.getGroupName(SlackId.group("S1"));
        String result2 = slackClient.getGroupName(SlackId.group("S1")); // cache hit

        // Then
        assertThat(result1).isEqualTo("handle-1");
        assertThat(result2).isEqualTo("handle-1");
        verify(methodsClient, times(1)).usergroupsList(any(UsergroupsListRequest.class));
    }

    @Test
    void getGroupNameReturnsNullOnError() throws Exception {
        // Given
        when(methodsClient.usergroupsList(any(UsergroupsListRequest.class)))
            .thenThrow(new java.io.IOException("IO error"));

        // When
        String result = slackClient.getGroupName(SlackId.group("S1"));

        // Then
        assertThat(result).isNull();
    }

    @Test
    void getChannelNameUsesCache() {
        // Given
        channelCache.put("C1", "cached-channel");

        // When
        String result = slackClient.getChannelName("C1");

        // Then
        assertThat(result).isEqualTo("cached-channel");
        verifyNoInteractions(methodsClient);
    }

    @Test
    void getChannelNameFetchesAndCaches() throws Exception {
        // Given
        com.slack.api.model.Conversation channel = new com.slack.api.model.Conversation();
        channel.setName("general");
        ConversationsInfoResponse response = new ConversationsInfoResponse();
        response.setOk(true);
        response.setChannel(channel);
        when(methodsClient.conversationsInfo(any(ConversationsInfoRequest.class))).thenReturn(response);

        // When
        String result1 = slackClient.getChannelName("C1");
        String result2 = slackClient.getChannelName("C1"); // cache hit

        // Then
        assertThat(result1).isEqualTo("general");
        assertThat(result2).isEqualTo("general");
        verify(methodsClient, times(1)).conversationsInfo(any(ConversationsInfoRequest.class));
    }

    @Test
    void getChannelNameReturnsNullOnError() throws Exception {
        // Given
        when(methodsClient.conversationsInfo(any(ConversationsInfoRequest.class)))
            .thenThrow(new java.io.IOException("IO error"));

        // When
        String result = slackClient.getChannelName("C1");

        // Then
        assertThat(result).isNull();
    }
}

