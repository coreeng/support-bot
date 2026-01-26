package com.coreeng.supportbot.ticket.rest;

import com.coreeng.supportbot.config.TicketAssignmentProps;
import com.coreeng.supportbot.escalation.rest.EscalationUIMapper;
import com.coreeng.supportbot.slack.SlackId;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.coreeng.supportbot.slack.client.SlackGetMessageByTsRequest;
import com.coreeng.supportbot.teams.SupportTeamService;
import com.coreeng.supportbot.teams.Team;
import com.coreeng.supportbot.teams.TeamMemberFetcher;
import com.coreeng.supportbot.teams.TeamService;
import com.coreeng.supportbot.teams.rest.TeamUI;
import com.coreeng.supportbot.teams.rest.TeamUIMapper;
import com.coreeng.supportbot.ticket.DetailedTicket;
import com.coreeng.supportbot.ticket.TicketTeam;
import com.google.common.collect.ImmutableList;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static com.google.common.collect.ImmutableList.toImmutableList;

@Component
@RequiredArgsConstructor
public class TicketUIMapper {
    private final EscalationUIMapper escalationUIMapper;
    private final SlackClient slackClient;
    private final TeamService teamService;
    private final TeamUIMapper teamUIMapper;
    private final SupportTeamService supportTeamService;
    private final TicketAssignmentProps assignmentProps;

    public TicketUI mapToUI(DetailedTicket ticket) {
        return mapToUI(ticket, null);
    }

    public TicketUI mapToUI(DetailedTicket ticket, String queryText) {
        return TicketUI.builder()
            .id(ticket.ticket().id())
            .query(new TicketUI.Query(
                slackClient.getPermalink(new SlackGetMessageByTsRequest(
                    ticket.ticket().channelId(),
                    ticket.ticket().queryTs()
                )),
                ticket.ticket().queryTs().getDate(),
                ticket.ticket().queryTs(),
                queryText
            ))
            .formMessage(new TicketUI.FormMessage(
                ticket.ticket().createdMessageTs()
            ))
            .channelId(ticket.ticket().channelId())
            .status(ticket.ticket().status())
            .team(
                switch (ticket.ticket().team()) {
                    case null -> null;
                    case TicketTeam.UnknownTeam u -> new TeamUI(
                        TicketTeam.notATenantCode,
                        TicketTeam.notATenantCode,
                        ImmutableList.of()
                    );
                    case TicketTeam.KnownTeam k -> mapKnownTeamToUI(k.code());
                }
            )
            .impact(ticket.ticket().impact())
            .tags(ticket.ticket().tags())
            .logs(
                ticket.ticket().statusLog().stream()
                    .map(s -> new TicketUI.Log(
                        s.date(),
                        switch (s.status()) {
                            case opened -> TicketUI.LogEvent.opened;
                            case stale ->  TicketUI.LogEvent.stale;
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
            .ratingSubmitted(ticket.ticket().ratingSubmitted())
            .assignedTo(getAssigneeEmail(ticket.ticket().assignedTo()))
            .build();
    }

    // TODO: If team is deleted after ticket has been saved to to db, this returns null. We should potentially look at saving team info to database so deleted teams can still be displayed instead of returning null
    private TeamUI mapKnownTeamToUI(String code) {
        Team team = teamService.findTeamByCode(code);
        return team != null ? teamUIMapper.mapToUI(team) : null;
    }

    private String getAssigneeEmail(SlackId.User assignedToUserId) {
        if (assignedToUserId == null || !assignmentProps.enabled()) {
            return null;
        }

        // Stream operations with orElse(null) are safe - if user not found, returns null gracefully
        return supportTeamService.members().stream()
            .filter(member -> assignedToUserId.equals(member.slackId()))
            .findFirst()
            .map(TeamMemberFetcher.TeamMember::email)
            .orElse(null);
    }
}
