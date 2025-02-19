package com.coreeng.supportbot.homepage.handler;

import com.coreeng.supportbot.homepage.HomepageFilter;
import com.coreeng.supportbot.homepage.HomepageFilterMapper;
import com.coreeng.supportbot.homepage.HomepageOperation;
import com.coreeng.supportbot.homepage.HomepageService;
import com.coreeng.supportbot.homepage.HomepageView;
import com.coreeng.supportbot.homepage.HomepageViewMapper;
import com.coreeng.supportbot.slack.SlackViewSubmitHandler;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.slack.api.app_backend.views.response.ViewSubmissionResponse;
import com.slack.api.bolt.context.builtin.ViewSubmissionContext;
import com.slack.api.bolt.request.builtin.ViewSubmissionRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class HomepageFilterSubmissionHandler implements SlackViewSubmitHandler {
    private final HomepageService homepageService;
    private final HomepageFilterMapper filterMapper;
    private final HomepageViewMapper viewMapper;
    private final SlackClient slackClient;

    @Override
    public Pattern getPattern() {
        return Pattern.compile("^" + HomepageOperation.filter.actionId() + "$");
    }

    @Override
    public ViewSubmissionResponse apply(ViewSubmissionRequest request, ViewSubmissionContext context) {
        HomepageFilter filter = filterMapper.extractSubmittedValues(request.getPayload().getView());
        HomepageView view = homepageService.getTicketsView(
            HomepageView.State.builder()
                .filter(filter)
                .build()
        );
        slackClient.updateHomeView(
            request.getPayload().getUser().getId(),
            viewMapper.render(view)
        );
        return new ViewSubmissionResponse();
    }
}
