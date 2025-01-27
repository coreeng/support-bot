package com.coreeng.supportbot.teams.handler;

import com.coreeng.supportbot.slack.SlackEventHandler;
import com.coreeng.supportbot.teams.SupportTeamService;
import com.google.common.collect.ImmutableList;
import com.slack.api.app_backend.events.payload.EventsApiPayload;
import com.slack.api.bolt.context.builtin.EventContext;
import com.slack.api.methods.SlackApiException;
import com.slack.api.model.event.SubteamUpdatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class SubTeamUpdatedEventHandler implements SlackEventHandler<SubteamUpdatedEvent> {
    private final SupportTeamService supportTeamService;

    @Override
    public Class<SubteamUpdatedEvent> getEventClass() {
        return SubteamUpdatedEvent.class;
    }

    @Override
    public void apply(EventsApiPayload<SubteamUpdatedEvent> event, EventContext context) throws IOException, SlackApiException {
        ImmutableList<String> teamUsers = ImmutableList.copyOf(event.getEvent().getSubteam().getUsers());
        supportTeamService.handleMembershipUpdate(
            event.getEvent().getSubteam().getId(),
            teamUsers
        );
    }
}
