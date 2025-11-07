package com.coreeng.supportbot.escalation.rest;

import com.coreeng.supportbot.escalation.Escalation;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.coreeng.supportbot.slack.client.SlackGetMessageByTsRequest;
import com.coreeng.supportbot.teams.TeamService;
import com.coreeng.supportbot.teams.rest.TeamUIMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static com.google.common.base.Preconditions.checkNotNull;

@Component
@RequiredArgsConstructor
public class EscalationUIMapper {
    private final SlackClient slackClient;
    private final TeamService teamService;
    private final TeamUIMapper teamUIMapper;

    public EscalationUI mapToUI(Escalation escalation) {
        return EscalationUI.builder()
            .id(escalation.id())
            .ticketId(escalation.ticketId())
            .threadLink(slackClient.getPermalink(new SlackGetMessageByTsRequest(
                escalation.channelId(),
                escalation.threadTs()
            )))
            .openedAt(escalation.openedAt())
            .resolvedAt(escalation.resolvedAt())
            .team(teamUIMapper.mapToUI(checkNotNull(teamService.findTeamByCode(escalation.team()))))
            .tags(escalation.tags())
            .build();
    }
}
