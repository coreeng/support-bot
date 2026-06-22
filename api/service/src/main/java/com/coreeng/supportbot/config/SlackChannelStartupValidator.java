package com.coreeng.supportbot.config;

import com.coreeng.supportbot.config.SlackChannelProps.TrackMode;
import com.google.common.collect.ImmutableList;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Fails fast on Slack channel configuration that would silently leave the bot doing nothing.
 *
 * <p>Two cases are rejected at startup, mirroring {@code PrTrackingStartupValidator}:
 *
 * <ul>
 *   <li>No channels resolved at all (neither {@code slack.ticket.channel-id} nor {@code
 *       slack.ticket.channels}) — the bot would monitor nothing.
 *   <li>A channel with {@link TrackMode#PRS} while {@code pr-review-tracking.enabled} is {@code
 *       false}: a PRS channel relies entirely on PR-link detection (the normal query/reaction flow
 *       is suppressed), so with the {@code PrDetectionService} bean absent it would never create any
 *       tickets.
 * </ul>
 */
@Component
@Order(102)
@Slf4j
public class SlackChannelStartupValidator implements ApplicationRunner {

    private final SlackChannelRegistry channelRegistry;
    private final boolean prTrackingEnabled;

    public SlackChannelStartupValidator(
            SlackChannelRegistry channelRegistry,
            @Value("${pr-review-tracking.enabled:false}") boolean prTrackingEnabled) {
        this.channelRegistry = channelRegistry;
        this.prTrackingEnabled = prTrackingEnabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        ImmutableList<String> channelIds = channelRegistry.monitoredChannelIds();
        if (channelIds.isEmpty()) {
            throw new IllegalStateException("No Slack channels are configured. Set slack.ticket.channel-id "
                    + "or at least one entry under slack.ticket.channels.");
        }

        if (prTrackingEnabled) {
            return;
        }
        for (String channelId : channelIds) {
            SlackChannelProps channel = channelRegistry.find(channelId).orElseThrow();
            if (channel.track() == TrackMode.PRS) {
                throw new IllegalStateException(
                        ("slack.ticket.channels: channel '%s' (%s) is configured track: PRS but "
                                        + "pr-review-tracking.enabled is false, so it would never create any tickets. "
                                        + "Enable pr-review-tracking or change the channel's track mode.")
                                .formatted(channel.name(), channel.id()));
            }
        }
    }
}
