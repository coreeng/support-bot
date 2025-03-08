package com.coreeng.supportbot.config;

import com.coreeng.supportbot.enums.EscalationTeam;
import com.coreeng.supportbot.enums.Tag;
import com.coreeng.supportbot.enums.TicketImpact;
import com.google.common.collect.ImmutableList;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

import static com.google.common.collect.Iterables.isEmpty;

@ConfigurationProperties(prefix = "enums")
@Getter
public class EnumProps {
    private final ImmutableList<EscalationTeam> escalationTeams;
    private final ImmutableList<Tag> tags;
    private final ImmutableList<TicketImpact> impacts;

    public EnumProps(
        List<EscalationTeam> escalationTeams,
        List<Tag> tags,
        List<TicketImpact> impacts
    ) {
        this.escalationTeams = isEmpty(escalationTeams)
            ? ImmutableList.of()
            : ImmutableList.copyOf(escalationTeams);
        this.tags = isEmpty(tags)
            ? ImmutableList.of()
            : ImmutableList.copyOf(tags);
        this.impacts = isEmpty(impacts)
            ? ImmutableList.of()
            : ImmutableList.copyOf(impacts);
    }
}
