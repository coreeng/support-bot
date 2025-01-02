package com.coreeng.supportbot.config;

import com.coreeng.supportbot.EnumerationValue;
import com.google.common.collect.ImmutableList;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

import static com.google.common.collect.Iterables.isEmpty;

@ConfigurationProperties(prefix = "ticket")
@Getter
public class TicketProps {
    private final ImmutableList<EnumerationValue> tags;
    private final ImmutableList<EnumerationValue> impacts;

    public TicketProps(List<EnumerationValue> tags, List<EnumerationValue> impacts) {
        this.tags = isEmpty(tags)
            ? ImmutableList.of()
            : ImmutableList.copyOf(tags);
        this.impacts = isEmpty(impacts)
            ? ImmutableList.of()
            : ImmutableList.copyOf(impacts);
    }
}
