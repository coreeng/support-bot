package com.coreeng.supportbot.config;

import static org.apache.commons.lang3.StringUtils.isBlank;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/**
 * Configuration for a single monitored Slack channel.
 *
 * <p>One running bot can monitor multiple channels, each with its own {@link TrackMode} that
 * controls whether normal support queries, PR-link tickets, or both are tracked in that channel.
 *
 * @param name human-readable label for the channel (for logs/config readability only)
 * @param id Slack channel ID (e.g. {@code C1234567890})
 * @param track what this channel tracks; defaults to {@link TrackMode#BOTH}
 */
public record SlackChannelProps(String name, String id, TrackMode track) {

    @ConstructorBinding
    public SlackChannelProps(@Nullable String name, String id, @Nullable TrackMode track) {
        if (isBlank(id)) {
            throw new IllegalArgumentException("slack.ticket.channels[].id must not be blank");
        }
        this.id = id;
        this.track = track == null ? TrackMode.BOTH : track;
        // Fall back to the id when no name is given so logs always have something to print.
        this.name = isBlank(name) ? id : name;
    }

    /** What a channel tracks. */
    public enum TrackMode {
        /** Only normal support queries (reactions + thread tickets); PR detection disabled. */
        QUERIES,
        /** Only PR-link tickets; the normal query/reaction flow is suppressed. */
        PRS,
        /** Both normal queries and PR-link tickets. */
        BOTH;

        public boolean tracksQueries() {
            return this == QUERIES || this == BOTH;
        }

        public boolean tracksPrs() {
            return this == PRS || this == BOTH;
        }
    }
}
