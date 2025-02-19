package com.coreeng.supportbot.escalation.handler;

import com.coreeng.supportbot.escalation.EscalationCreatedMessageMapper;
import com.coreeng.supportbot.escalation.EscalationOperation;
import com.coreeng.supportbot.escalation.EscalationResolveInput;
import com.coreeng.supportbot.escalation.EscalationProcessingService;
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
    private final EscalationProcessingService processingService;
    private final EscalationCreatedMessageMapper createdMessageMapper;

    @Override
    public Pattern getPattern() {
        return EscalationOperation.pattern;
    }

    @Override
    public void apply(BlockActionRequest req, ActionContext context) throws IOException, SlackApiException {
        for (BlockActionPayload.Action action : req.getPayload().getActions()) {
            EscalationOperation escalationOperation = EscalationOperation.fromActionIdOrNull(action.getActionId());
            switch (escalationOperation) {
                case resolve -> {
                    EscalationResolveInput input = createdMessageMapper.parseTriggerInput(action.getValue());
                    processingService.resolve(input.escalationId());
                }
                case null -> {
                    log.atWarn()
                        .addArgument(action.getActionId())
                        .log("Unknown escalation action: {}");
                }
            }
        }
    }
}
