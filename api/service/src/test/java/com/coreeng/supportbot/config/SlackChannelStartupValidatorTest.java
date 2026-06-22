package com.coreeng.supportbot.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coreeng.supportbot.config.SlackChannelProps.TrackMode;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class SlackChannelStartupValidatorTest {

    private static SlackChannelRegistry registry(@Nullable String channelId, List<SlackChannelProps> channels) {
        return new SlackChannelRegistry(
                new SlackTicketsProps(channelId, channels, "eyes", "ticket", "white_check_mark", "rocket"));
    }

    private static void validate(SlackChannelRegistry registry, boolean prTrackingEnabled) {
        new SlackChannelStartupValidator(registry, prTrackingEnabled).run(new DefaultApplicationArguments());
    }

    @Test
    void failsWhenPrsChannelConfiguredButPrTrackingDisabled() {
        SlackChannelRegistry registry = registry(null, List.of(new SlackChannelProps("prs", "C-prs", TrackMode.PRS)));

        assertThatThrownBy(() -> validate(registry, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("C-prs")
                .hasMessageContaining("pr-review-tracking");
    }

    @Test
    void passesWhenPrsChannelConfiguredAndPrTrackingEnabled() {
        SlackChannelRegistry registry = registry(null, List.of(new SlackChannelProps("prs", "C-prs", TrackMode.PRS)));

        assertThatCode(() -> validate(registry, true)).doesNotThrowAnyException();
    }

    @Test
    void passesForQueriesAndBothChannelsWhenPrTrackingDisabled() {
        SlackChannelRegistry registry = registry(
                null,
                List.of(
                        new SlackChannelProps("q", "C-queries", TrackMode.QUERIES),
                        new SlackChannelProps("b", "C-both", TrackMode.BOTH)));

        assertThatCode(() -> validate(registry, false)).doesNotThrowAnyException();
    }

    @Test
    void passesForLegacySingleChannelWhenPrTrackingDisabled() {
        SlackChannelRegistry registry = registry("C-legacy", List.of());

        assertThatCode(() -> validate(registry, false)).doesNotThrowAnyException();
    }

    @Test
    void failsWhenNoChannelsConfigured() {
        SlackChannelRegistry registry = registry(null, List.of());

        assertThatThrownBy(() -> validate(registry, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No Slack channels are configured");
    }
}
