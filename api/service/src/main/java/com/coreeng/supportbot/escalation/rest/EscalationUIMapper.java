package com.coreeng.supportbot.escalation.rest;

import com.coreeng.supportbot.escalation.Escalation;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.coreeng.supportbot.slack.client.SlackGetMessageByTsRequest;
import com.coreeng.supportbot.teams.Team;
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
        String threadLink = escalation.threadTs() != null
            ? slackClient.getPermalink(new SlackGetMessageByTsRequest(
                escalation.channelId(),
                escalation.threadTs()
            ))
            : null;
        Team team = escalation.team() != null ? teamService.findTeamByCode(escalation.team()) : null;
        return EscalationUI.builder()
            .id(checkNotNull(escalation.id()))
            .ticketId(escalation.ticketId())
            .threadLink(threadLink)
            .openedAt(escalation.openedAt())
            .resolvedAt(escalation.resolvedAt())
            .team(team != null ? teamUIMapper.mapToUI(team) : null)
            .tags(escalation.tags())
            .build();
    }
}
