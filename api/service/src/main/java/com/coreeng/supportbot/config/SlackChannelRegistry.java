package com.coreeng.supportbot.config;

import static org.apache.commons.lang3.StringUtils.isBlank;

import com.coreeng.supportbot.config.SlackChannelProps.TrackMode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Resolves and answers questions about the set of monitored Slack channels.
 *
 * <p>This is the single source of truth for "which channels does the bot watch, and what does each
 * track". It resolves the effective channel set from {@link SlackTicketsProps} once at startup:
 *
 * <ul>
 *   <li>If {@code slack.ticket.channels} is configured, those entries are used.
 *   <li>Otherwise, for backward compatibility, the legacy single {@code slack.ticket.channel-id} is
 *       synthesized into one channel tracking {@link TrackMode#BOTH}.
 * </ul>
 *
 * <p>Slack event handlers must gate on this registry so each channel's {@link TrackMode} is honoured:
 * call {@link #isMonitored} to decide whether to handle an event at all, and {@link
 * #shouldTrackQueries}/{@link #shouldTrackPrs} for the normal-query vs PR-link behaviour in the
 * event's channel. A handler that skips these checks will silently ignore per-channel track modes.
 */
@Component
@Slf4j
public class SlackChannelRegistry {

    private final ImmutableMap<String, SlackChannelProps> byId;

    public SlackChannelRegistry(SlackTicketsProps props) {
        this.byId = resolve(props);
        log.atInfo()
                .addKeyValue("channelCount", byId.size())
                .addKeyValue("channels", byId.values())
                .log("Resolved monitored Slack channels");
    }

    private static ImmutableMap<String, SlackChannelProps> resolve(SlackTicketsProps props) {
        ImmutableList<SlackChannelProps> channels;
        if (!props.channels().isEmpty()) {
            channels = ImmutableList.copyOf(props.channels());
        } else if (!isBlank(props.channelId())) {
            // Legacy single-channel config: behave exactly as before by tracking everything.
            channels = ImmutableList.of(new SlackChannelProps("default", props.channelId(), TrackMode.BOTH));
        } else {
            channels = ImmutableList.of();
        }

        // buildOrThrow() preserves insertion order and rejects duplicate channel ids (naming the
        // offending id), so monitoredChannelIds() and logs stay deterministic.
        ImmutableMap.Builder<String, SlackChannelProps> byId = ImmutableMap.builder();
        for (SlackChannelProps channel : channels) {
            byId.put(channel.id(), channel);
        }
        return byId.buildOrThrow();
    }

    /** Channel IDs of every monitored channel, in configuration order. */
    public ImmutableList<String> monitoredChannelIds() {
        return byId.keySet().asList();
    }

    /** True when the given channel is one the bot monitors at all. */
    public boolean isMonitored(String channelId) {
        return byId.containsKey(channelId);
    }

    /** True when normal support queries should be tracked in the given channel. */
    public boolean shouldTrackQueries(String channelId) {
        SlackChannelProps channel = byId.get(channelId);
        return channel != null && channel.track().tracksQueries();
    }

    /** True when PR-link tickets should be tracked in the given channel. */
    public boolean shouldTrackPrs(String channelId) {
        SlackChannelProps channel = byId.get(channelId);
        return channel != null && channel.track().tracksPrs();
    }

    public Optional<SlackChannelProps> find(String channelId) {
        return Optional.ofNullable(byId.get(channelId));
    }
}
