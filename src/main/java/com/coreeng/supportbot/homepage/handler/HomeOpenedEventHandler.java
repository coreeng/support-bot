package com.coreeng.supportbot.homepage.handler;

import com.coreeng.supportbot.homepage.HomepageService;
import com.coreeng.supportbot.homepage.HomepageView;
import com.coreeng.supportbot.homepage.HomepageViewMapper;
import com.coreeng.supportbot.slack.SlackEventHandler;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.slack.api.app_backend.events.payload.EventsApiPayload;
import com.slack.api.bolt.context.builtin.EventContext;
import com.slack.api.model.event.AppHomeOpenedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class HomeOpenedEventHandler implements SlackEventHandler<AppHomeOpenedEvent> {
    private final HomepageService homepageService;
    private final HomepageViewMapper viewMapper;
    private final SlackClient slackClient;

    @Override
    public Class<AppHomeOpenedEvent> getEventClass() {
        return AppHomeOpenedEvent.class;
    }

    @Override
    public void apply(EventsApiPayload<AppHomeOpenedEvent> event, EventContext context) {
        HomepageView homepageView = homepageService.getTicketsView(HomepageView.State.getDefault());
        slackClient.updateHomeView(event.getEvent().getUser(), viewMapper.render(homepageView));
    }
}
