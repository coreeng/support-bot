package com.coreeng.supportbot.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coreeng.supportbot.config.SlackChannelProps.TrackMode;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class SlackChannelRegistryTest {

    private static SlackTicketsProps props(@Nullable String channelId, List<SlackChannelProps> channels) {
        return new SlackTicketsProps(channelId, channels, "eyes", "ticket", "white_check_mark", "rocket");
    }

    @Test
    void fallsBackToLegacyChannelIdAsBoth() {
        SlackChannelRegistry registry = new SlackChannelRegistry(props("C-legacy", List.of()));

        assertThat(registry.monitoredChannelIds()).containsExactly("C-legacy");
        assertThat(registry.isMonitored("C-legacy")).isTrue();
        assertThat(registry.shouldTrackQueries("C-legacy")).isTrue();
        assertThat(registry.shouldTrackPrs("C-legacy")).isTrue();
    }

    @Test
    void channelsListTakesPrecedenceOverLegacyChannelId() {
        SlackChannelRegistry registry = new SlackChannelRegistry(
                props("C-legacy", List.of(new SlackChannelProps("primary", "C-new", TrackMode.BOTH))));

        // The legacy id is ignored once an explicit channels list is present.
        assertThat(registry.monitoredChannelIds()).containsExactly("C-new");
        assertThat(registry.isMonitored("C-legacy")).isFalse();
    }

    @Test
    void noChannelsConfiguredMonitorsNothing() {
        SlackChannelRegistry registry = new SlackChannelRegistry(props(null, List.of()));

        assertThat(registry.monitoredChannelIds()).isEmpty();
        assertThat(registry.isMonitored("anything")).isFalse();
        assertThat(registry.shouldTrackQueries("anything")).isFalse();
        assertThat(registry.shouldTrackPrs("anything")).isFalse();
    }

    @Test
    void perChannelTrackModeIsHonoured() {
        SlackChannelRegistry registry = new SlackChannelRegistry(props(
                null,
                List.of(
                        new SlackChannelProps("q", "C-queries", TrackMode.QUERIES),
                        new SlackChannelProps("p", "C-prs", TrackMode.PRS),
                        new SlackChannelProps("b", "C-both", TrackMode.BOTH))));

        assertThat(registry.monitoredChannelIds()).containsExactly("C-queries", "C-prs", "C-both");

        assertThat(registry.shouldTrackQueries("C-queries")).isTrue();
        assertThat(registry.shouldTrackPrs("C-queries")).isFalse();

        assertThat(registry.shouldTrackQueries("C-prs")).isFalse();
        assertThat(registry.shouldTrackPrs("C-prs")).isTrue();

        assertThat(registry.shouldTrackQueries("C-both")).isTrue();
        assertThat(registry.shouldTrackPrs("C-both")).isTrue();
    }

    @Test
    void unknownChannelTracksNothing() {
        SlackChannelRegistry registry =
                new SlackChannelRegistry(props(null, List.of(new SlackChannelProps("b", "C-both", TrackMode.BOTH))));

        assertThat(registry.isMonitored("C-other")).isFalse();
        assertThat(registry.shouldTrackQueries("C-other")).isFalse();
        assertThat(registry.shouldTrackPrs("C-other")).isFalse();
    }

    @Test
    void duplicateChannelIdsAreRejected() {
        assertThatThrownBy(() -> new SlackChannelRegistry(props(
                        null,
                        List.of(
                                new SlackChannelProps("a", "C-dup", TrackMode.BOTH),
                                new SlackChannelProps("b", "C-dup", TrackMode.QUERIES)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("C-dup");
    }

    @Test
    void trackModeDefaultsToBothWhenOmitted() {
        SlackChannelProps channel = new SlackChannelProps("name", "C1", null);
        assertThat(channel.track()).isEqualTo(TrackMode.BOTH);
    }

    @Test
    void channelNameFallsBackToIdWhenBlank() {
        SlackChannelProps channel = new SlackChannelProps(" ", "C1", TrackMode.BOTH);
        assertThat(channel.name()).isEqualTo("C1");
    }

    @Test
    void blankChannelIdIsRejected() {
        assertThatThrownBy(() -> new SlackChannelProps("name", " ", TrackMode.BOTH))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
