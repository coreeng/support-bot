package com.coreeng.supportbot.escalation.command.handler;

import com.coreeng.supportbot.escalation.command.ButtonAction;
import com.coreeng.supportbot.escalation.command.CommandButton;
import com.coreeng.supportbot.escalation.command.CommandMainService;
import com.coreeng.supportbot.slack.MessageRef;
import com.coreeng.supportbot.slack.SlackBlockActionHandler;
import com.slack.api.app_backend.interactive_components.payload.BlockActionPayload;
import com.slack.api.bolt.context.builtin.ActionContext;
import com.slack.api.bolt.request.builtin.BlockActionRequest;
import com.slack.api.methods.SlackApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
@Slf4j
public class ActionHandler implements SlackBlockActionHandler {
    private final CommandMainService commandMainService;

    @Override
    public Pattern getPattern() {
        return CommandButton.pattern;
    }

    @Override
    public void apply(BlockActionRequest req, ActionContext context) throws IOException, SlackApiException {
        for (BlockActionPayload.Action action : req.getPayload().getActions()) {
            CommandButton button = CommandButton.valueOfOrNull(action.getActionId());
            if (button != null) {
                commandMainService.handleAction(new ButtonAction(
                    MessageRef.from(req.getPayload()),
                    button
                ));
            } else {
                log.atWarn()
                    .addArgument(action::getActionId)
                    .log("Unknown action: {}");
            }
        }
    }
}
