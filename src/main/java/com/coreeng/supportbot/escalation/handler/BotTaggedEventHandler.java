package com.coreeng.supportbot.escalation.handler;

import com.coreeng.supportbot.escalation.command.CommandMainService;
import com.coreeng.supportbot.slack.SlackEventHandler;
import com.coreeng.supportbot.slack.events.BotTagged;
import com.slack.api.app_backend.events.payload.EventsApiPayload;
import com.slack.api.bolt.context.builtin.EventContext;
import com.slack.api.methods.SlackApiException;
import com.slack.api.model.event.AppMentionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class BotTaggedEventHandler implements SlackEventHandler<AppMentionEvent> {
    private final CommandMainService commandMainService;

    @Override
    public Class<AppMentionEvent> getEventClass() {
        return AppMentionEvent.class;
    }

    @Override
    public void apply(EventsApiPayload<AppMentionEvent> event, EventContext context) throws IOException, SlackApiException {
        commandMainService.handleCommand(BotTagged.fromRaw(event));
    }
}
