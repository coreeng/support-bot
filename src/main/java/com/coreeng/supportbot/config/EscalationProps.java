package com.coreeng.supportbot.config;

import com.coreeng.supportbot.EnumerationValue;
import com.google.common.collect.ImmutableList;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

import static com.google.common.collect.Iterables.isEmpty;

@ConfigurationProperties(prefix = "escalation")
@Getter
public class EscalationProps {
    private final ImmutableList<EnumerationValue> topics;
    private final ImmutableList<EnumerationValue> teams;

    public EscalationProps(List<EnumerationValue> teams, List<EnumerationValue> topics) {
        this.teams = isEmpty(teams)
            ? ImmutableList.of()
            : ImmutableList.copyOf(teams);
        this.topics = isEmpty(topics)
            ? ImmutableList.of()
            : ImmutableList.copyOf(topics);
    }
}
