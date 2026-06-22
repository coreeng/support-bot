package com.coreeng.supportbot.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.coreeng.supportbot.config.SlackChannelProps.TrackMode;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

/**
 * Verifies that {@code slack.ticket} YAML-style properties actually bind onto {@link
 * SlackTicketsProps} — including the nested {@code channels} list and its {@link TrackMode} enum.
 *
 * <p>The other unit tests construct {@link SlackTicketsProps} in code, which bypasses Spring's
 * constructor binding; this is the only place the real binding path (relaxed names, enum parsing,
 * indexed list elements) is exercised.
 */
class SlackTicketsPropsBindingTest {

    private static SlackTicketsProps bind(Map<String, Object> properties) {
        Binder binder = new Binder(new MapConfigurationPropertySource(properties));
        return binder.bind("slack.ticket", SlackTicketsProps.class).get();
    }

    private static Map<String, Object> baseProps() {
        Map<String, Object> map = new HashMap<>();
        map.put("slack.ticket.expected-initial-reaction", "eyes");
        map.put("slack.ticket.response-initial-reaction", "ticket");
        map.put("slack.ticket.resolved-reaction", "white_check_mark");
        map.put("slack.ticket.escalated-reaction", "rocket");
        return map;
    }

    @Test
    void bindsChannelsListWithTrackModeFromYamlStyleProperties() {
        Map<String, Object> map = baseProps();
        map.put("slack.ticket.channels[0].name", "prs");
        map.put("slack.ticket.channels[0].id", "C-prs");
        map.put("slack.ticket.channels[0].track", "PRS");
        map.put("slack.ticket.channels[1].name", "queries");
        map.put("slack.ticket.channels[1].id", "C-queries");
        map.put("slack.ticket.channels[1].track", "QUERIES");

        SlackTicketsProps props = bind(map);

        assertThat(props.channels()).hasSize(2);
        assertThat(props.channels().get(0).id()).isEqualTo("C-prs");
        assertThat(props.channels().get(0).track()).isEqualTo(TrackMode.PRS);
        assertThat(props.channels().get(1).id()).isEqualTo("C-queries");
        assertThat(props.channels().get(1).track()).isEqualTo(TrackMode.QUERIES);

        // And the bound channels resolve end to end through the registry.
        SlackChannelRegistry registry = new SlackChannelRegistry(props);
        assertThat(registry.shouldTrackPrs("C-prs")).isTrue();
        assertThat(registry.shouldTrackQueries("C-prs")).isFalse();
        assertThat(registry.shouldTrackQueries("C-queries")).isTrue();
        assertThat(registry.shouldTrackPrs("C-queries")).isFalse();
    }

    @Test
    void trackModeBindingIsCaseInsensitive() {
        Map<String, Object> map = baseProps();
        map.put("slack.ticket.channels[0].id", "C1");
        map.put("slack.ticket.channels[0].track", "prs");

        SlackTicketsProps props = bind(map);

        assertThat(props.channels().get(0).track()).isEqualTo(TrackMode.PRS);
    }

    @Test
    void trackModeDefaultsToBothWhenOmittedInProperties() {
        Map<String, Object> map = baseProps();
        map.put("slack.ticket.channels[0].id", "C1");

        SlackTicketsProps props = bind(map);

        assertThat(props.channels().get(0).track()).isEqualTo(TrackMode.BOTH);
    }

    @Test
    void emptyChannelsBindsToEmptyListNotNull() {
        SlackTicketsProps props = bind(baseProps());

        assertThat(props.channels()).isEmpty();
    }

    @Test
    void legacyChannelIdBindsAndFallsBackToBoth() {
        Map<String, Object> map = baseProps();
        map.put("slack.ticket.channel-id", "C-legacy");

        SlackTicketsProps props = bind(map);

        assertThat(props.channelId()).isEqualTo("C-legacy");
        SlackChannelRegistry registry = new SlackChannelRegistry(props);
        assertThat(registry.shouldTrackQueries("C-legacy")).isTrue();
        assertThat(registry.shouldTrackPrs("C-legacy")).isTrue();
    }
}
