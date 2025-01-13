package com.coreeng.supportbot.escalation.handler;

import com.coreeng.supportbot.escalation.EscalationOperation;
import com.coreeng.supportbot.escalation.EscalationFormMapper;
import com.coreeng.supportbot.escalation.EscalationService;
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
public class EscalationActionHandler implements SlackBlockActionHandler {
    private final EscalationService escalationService;
    private final EscalationFormMapper escalationFormMapper;

    @Override
    public Pattern getPattern() {
        return EscalationOperation.pattern;
    }

    @Override
    public void apply(BlockActionRequest req, ActionContext context) throws IOException, SlackApiException {
        for (BlockActionPayload.Action action : req.getPayload().getActions()) {
            EscalationOperation escalationOperation = EscalationOperation.fromActionIdOrNull(action.getActionId());
            if (escalationOperation != null) {
                MessageRef messageRef = MessageRef.from(req.getPayload());
                switch (escalationOperation) {
                    case confirm -> escalationService.openEscalation(messageRef);
                    case changeTopic, changeTeam ->
                        escalationService.updateEscalation(escalationFormMapper.mapToRequest(action, messageRef));
                }
            } else {
                log.atWarn()
                    .addArgument(action.getActionId())
                    .log("Unknown escalation action: {}");
            }
        }
    }
}
