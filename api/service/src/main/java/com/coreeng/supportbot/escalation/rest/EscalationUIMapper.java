package com.coreeng.supportbot.escalation.rest;

import static com.google.common.base.Preconditions.checkNotNull;

import com.coreeng.supportbot.escalation.Escalation;
import com.coreeng.supportbot.teams.Team;
import com.coreeng.supportbot.teams.TeamService;
import com.coreeng.supportbot.teams.rest.TeamUIMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EscalationUIMapper {
    private final TeamService teamService;
    private final TeamUIMapper teamUIMapper;

    public EscalationUI mapToUI(Escalation escalation) {
        Team team = escalation.team() != null ? teamService.findTeamByCode(escalation.team()) : null;
        return EscalationUI.builder()
                .id(checkNotNull(escalation.id()))
                .ticketId(escalation.ticketId())
                .hasThread(escalation.threadTs() != null)
                .openedAt(escalation.openedAt())
                .resolvedAt(escalation.resolvedAt())
                .team(team != null ? teamUIMapper.mapToUI(team) : null)
                .tags(escalation.tags())
                .build();
    }
}
