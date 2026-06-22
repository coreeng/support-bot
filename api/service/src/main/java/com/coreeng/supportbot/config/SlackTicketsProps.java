package com.coreeng.supportbot.config;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "slack.ticket")
public record SlackTicketsProps(
        @Nullable String channelId,
        List<SlackChannelProps> channels,
        String expectedInitialReaction,
        String responseInitialReaction,
        String resolvedReaction,
        String escalatedReaction) {

    @ConstructorBinding
    public SlackTicketsProps(
            @Nullable String channelId,
            @Nullable List<SlackChannelProps> channels,
            String expectedInitialReaction,
            String responseInitialReaction,
            String resolvedReaction,
            String escalatedReaction) {
        this.channelId = channelId;
        this.channels = channels == null ? List.of() : List.copyOf(channels);
        this.expectedInitialReaction = expectedInitialReaction;
        this.responseInitialReaction = responseInitialReaction;
        this.resolvedReaction = resolvedReaction;
        this.escalatedReaction = escalatedReaction;
    }
}
