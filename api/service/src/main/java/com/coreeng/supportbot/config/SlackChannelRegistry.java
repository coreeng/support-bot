package com.coreeng.supportbot.config;

import static org.apache.commons.lang3.StringUtils.isBlank;

import com.coreeng.supportbot.config.SlackChannelProps.TrackMode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.LinkedHashMap;
import java.util.Map;
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

        // Keep insertion order (LinkedHashMap) so monitoredChannelIds() and logs are deterministic.
        Map<String, SlackChannelProps> map = new LinkedHashMap<>();
        for (SlackChannelProps channel : channels) {
            if (map.putIfAbsent(channel.id(), channel) != null) {
                throw new IllegalArgumentException(
                        "slack.ticket.channels contains a duplicate channel id: " + channel.id());
            }
        }
        return ImmutableMap.copyOf(map);
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
