package com.coreeng.supportbot.escalation.handler;

import com.coreeng.supportbot.escalation.EscalationCreatedMessageMapper;
import com.coreeng.supportbot.escalation.EscalationOperation;
import com.coreeng.supportbot.escalation.EscalationResolveInput;
import com.coreeng.supportbot.escalation.EscalationProcessingService;
import com.coreeng.supportbot.rbac.RbacService;
import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.slack.SlackBlockActionHandler;
import com.coreeng.supportbot.slack.SlackId;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.coreeng.supportbot.slack.client.SlackPostEphemeralMessageRequest;
import com.coreeng.supportbot.rbac.RbacRestrictionMessage;
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
    private final RbacService rbacService;
    private final SlackClient slackClient;

    @Override
    public Pattern getPattern() {
        return EscalationOperation.pattern;
    }

    @Override
    public void apply(BlockActionRequest req, ActionContext context) throws IOException, SlackApiException {
        BlockActionPayload payload = req.getPayload();
        String userId = payload.getUser().getId();
        if (!rbacService.isSupportBySlackId(SlackId.user(userId))) {
            log.info("Skipping escalation action. User({}) is not a support team member", userId);
            slackClient.postEphemeralMessage(SlackPostEphemeralMessageRequest.builder()
                .message(new RbacRestrictionMessage())
                .channel(payload.getChannel().getId())
                .threadTs(MessageTs.ofOrNull(payload.getMessage().getThreadTs()))
                .userId(userId)
                .build()
            );
            return;
        }

        for (BlockActionPayload.Action action : payload.getActions()) {
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
