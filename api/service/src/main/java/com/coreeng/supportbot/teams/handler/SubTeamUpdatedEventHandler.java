package com.coreeng.supportbot.teams.handler;

import com.coreeng.supportbot.slack.SlackEventHandler;
import com.coreeng.supportbot.slack.SlackId;
import com.coreeng.supportbot.teams.SupportTeamService;
import com.google.common.collect.ImmutableList;
import com.slack.api.app_backend.events.payload.EventsApiPayload;
import com.slack.api.bolt.context.builtin.EventContext;
import com.slack.api.methods.SlackApiException;
import com.slack.api.model.event.SubteamUpdatedEvent;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SubTeamUpdatedEventHandler implements SlackEventHandler<SubteamUpdatedEvent> {
    private final SupportTeamService supportTeamService;

    @Override
    public Class<SubteamUpdatedEvent> getEventClass() {
        return SubteamUpdatedEvent.class;
    }

    @Override
    public void apply(EventsApiPayload<SubteamUpdatedEvent> event, EventContext context)
            throws IOException, SlackApiException {
        ImmutableList<SlackId.User> teamUsers = event.getEvent().getSubteam().getUsers().stream()
                .map(SlackId::user)
                .collect(ImmutableList.toImmutableList());
        supportTeamService.handleMembershipUpdate(
                SlackId.group(event.getEvent().getSubteam().getId()), teamUsers);
    }
}
