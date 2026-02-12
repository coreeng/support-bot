package com.coreeng.supportbot.homepage.handler;

import static com.slack.api.model.view.Views.view;

import com.coreeng.supportbot.homepage.HomepageFilterMapper;
import com.coreeng.supportbot.homepage.HomepageOperation;
import com.coreeng.supportbot.homepage.HomepageService;
import com.coreeng.supportbot.homepage.HomepageView;
import com.coreeng.supportbot.homepage.HomepageViewMapper;
import com.coreeng.supportbot.slack.SlackBlockActionHandler;
import com.coreeng.supportbot.slack.SlackId;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.slack.api.app_backend.interactive_components.payload.BlockActionPayload;
import com.slack.api.bolt.context.builtin.ActionContext;
import com.slack.api.bolt.request.builtin.BlockActionRequest;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.request.views.ViewsOpenRequest;
import java.io.IOException;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class HomepageActionHandler implements SlackBlockActionHandler {
    private final HomepageService homepageService;
    private final HomepageViewMapper viewMapper;
    private final HomepageFilterMapper filterMapper;
    private final SlackClient slackClient;

    @Override
    public Pattern getPattern() {
        return HomepageOperation.PATTERN;
    }

    @Override
    public void apply(BlockActionRequest req, ActionContext context) throws IOException, SlackApiException {
        for (BlockActionPayload.Action action : req.getPayload().getActions()) {
            HomepageOperation op = HomepageOperation.fromActionIdOrNull(action.getActionId());
            switch (op) {
                case refresh -> {
                    String metadataJson = req.getPayload().getView().getPrivateMetadata();
                    HomepageView.State currentState = viewMapper.parseMetadataOrDefault(metadataJson);
                    HomepageView homepageView = homepageService.getTicketsView(currentState);
                    slackClient.updateHomeView(
                            new SlackId.User(req.getPayload().getUser().getId()), viewMapper.render(homepageView));
                }
                case nextPage -> {
                    String metadataJson = req.getPayload().getView().getPrivateMetadata();
                    HomepageView.State currentState = viewMapper.parseMetadataOrDefault(metadataJson);
                    HomepageView homepageView = homepageService.getTicketsView(currentState.toBuilder()
                            .page(currentState.page() + 1)
                            .build());
                    slackClient.updateHomeView(
                            new SlackId.User(req.getPayload().getUser().getId()), viewMapper.render(homepageView));
                }
                case previousPage -> {
                    String metadataJson = req.getPayload().getView().getPrivateMetadata();
                    HomepageView.State currentState = viewMapper.parseMetadataOrDefault(metadataJson);
                    HomepageView homepageView = homepageService.getTicketsView(currentState.toBuilder()
                            .page(currentState.page() - 1)
                            .build());
                    slackClient.updateHomeView(
                            new SlackId.User(req.getPayload().getUser().getId()), viewMapper.render(homepageView));
                }
                case filter -> {
                    String metadataJson = req.getPayload().getView().getPrivateMetadata();
                    HomepageView.State currentState = viewMapper.parseMetadataOrDefault(metadataJson);
                    slackClient.viewsOpen(ViewsOpenRequest.builder()
                            .triggerId(context.getTriggerId())
                            .view(view(v -> filterMapper
                                    .render(currentState, v)
                                    .callbackId(HomepageOperation.filter.actionId())
                                    .type("modal")
                                    .clearOnClose(true)))
                            .build());
                }
                case null, default ->
                    log.atWarn().addArgument(action::getActionId).log("Unknown actionId detected - {}");
            }
        }
    }
}
