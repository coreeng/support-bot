package com.coreeng.supportbot.ticket.rest;

import com.coreeng.supportbot.escalation.rest.EscalationUIMapper;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.coreeng.supportbot.slack.client.SlackGetMessageByTsRequest;
import com.coreeng.supportbot.teams.TeamService;
import com.coreeng.supportbot.teams.rest.TeamUIMapper;
import com.coreeng.supportbot.ticket.DetailedTicket;
import com.google.common.collect.ImmutableList;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;

@Component
@RequiredArgsConstructor
public class TicketUIMapper {
    private final EscalationUIMapper escalationUIMapper;
    private final SlackClient slackClient;
    private final TeamService teamService;
    private final TeamUIMapper teamUIMapper;

    public TicketUI mapToUI(DetailedTicket ticket) {
        return TicketUI.builder()
            .id(ticket.ticket().id())
            .query(new TicketUI.Query(
                slackClient.getPermalink(new SlackGetMessageByTsRequest(
                    ticket.ticket().channelId(),
                    ticket.ticket().queryTs()
                )),
                ticket.ticket().queryTs().getDate()
            ))
            .status(ticket.ticket().status())
            .team(
                ticket.ticket().team() != null
                    ? teamUIMapper.mapToUI(checkNotNull(teamService.findTeamByName(ticket.ticket().team())))
                    : null
            )
            .impact(ticket.ticket().impact())
            .tags(ticket.ticket().tags())
            .logs(
                ticket.ticket().statusLog().stream()
                    .map(s -> new TicketUI.Log(
                        s.date(),
                        switch (s.status()) {
                            case opened -> TicketUI.LogEvent.opened;
                            case closed -> TicketUI.LogEvent.closed;
                        }
                    ))
                    .collect(toImmutableList())
            )
            .escalated(ticket.escalated())
            .escalations(
                ticket.escalations().isEmpty()
                    ? ImmutableList.of()
                    : ticket.escalations().stream()
                    .map(escalationUIMapper::mapToUI)
                    .collect(toImmutableList())
            )
            .build();
    }
}
