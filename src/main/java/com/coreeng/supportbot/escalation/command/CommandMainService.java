package com.coreeng.supportbot.escalation.command;

import com.coreeng.supportbot.config.SlackEscalationProps;
import com.coreeng.supportbot.escalation.EscalationService;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.coreeng.supportbot.slack.client.SlackPostMessageRequest;
import com.coreeng.supportbot.slack.events.BotTagged;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor
public class CommandMainService {
    private final static Pattern commandPattern = Pattern.compile("^<.+>(.*)$");

    private final EscalationService escalationService;
    private final SlackEscalationProps slackEscalationProps;
    private final SlackClient slackClient;

    public void handleCommand(BotTagged event) {
        if (!slackEscalationProps.channelId().equals(event.messageRef().channelId())) {
            return;
        }

        String message = event.message().trim();
        Matcher matcher = commandPattern.matcher(message);
        if (!matcher.matches()) {
            slackClient.postMessage(new SlackPostMessageRequest(
                new CommandHelpMessage(),
                event.messageRef().channelId(),
                event.messageRef().actualThreadTs()
            ));
            return;
        }

        String command = matcher.group(1).trim();
        switch (command) {
            case "" -> slackClient.postMessage(new SlackPostMessageRequest(
                new CommandButtonsMessage(),
                event.messageRef().channelId(),
                event.messageRef().actualThreadTs()
            ));
            case "resolve" -> escalationService.resolveEscalation(event.messageRef());
            default -> slackClient.postMessage(new SlackPostMessageRequest(
                new CommandHelpMessage(),
                event.messageRef().channelId(),
                event.messageRef().actualThreadTs()
            ));
        }
    }

    public void handleAction(ButtonAction action) {
        if (!slackEscalationProps.channelId().equals(action.messageRef().channelId())) {
            return;
        }

        switch (action.button()) {
            case escalate -> escalationService.createEscalation(action.messageRef());
        }
    }
}
