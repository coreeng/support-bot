package com.coreeng.supportbot.escalation;

import com.coreeng.supportbot.slack.MessageRef;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.coreeng.supportbot.slack.client.SlackPostMessageRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EscalationService {
    private final EscalationRepository repository;
    private final EscalationFormMapper formMapper;
    private final SlackClient slackClient;

    // This and other operations should be idempotent ideally.
    // If the message was not posted in previous time, it should do it now.
    // If the message was posted the previous time, it shouldn't do it again.
    // At the moment, we neglect this requirement.
    public void createEscalation(MessageRef reference) {
        Escalation escalation = repository.createIfNotExists(
            Escalation.createNew(
                reference.actualThreadTs(),
                reference.channelId()
            )
        );
        if (escalation == null) {
            log.warn("Escalation already exists for message: {}", reference);
            return;
        }

        log.atInfo()
            .addArgument(escalation::id)
            .log("Escalation created: {}");

        slackClient.postMessage(new SlackPostMessageRequest(
            new EscalationFormMessage(formMapper),
            reference.channelId(),
            reference.actualThreadTs()
        ));
    }

    public void updateEscalation(UpdateEscalationRequest request) {
        Escalation escalation = findEscalation(request.messageRef());
        Escalation.EscalationBuilder escalationBuilder = escalation.toBuilder();
        if (request.topic() != null) {
            escalationBuilder.topic(request.topic());
        }
        if (request.team() != null) {
            escalationBuilder.team(request.team());
        }
        Escalation updatedEscalation = repository.update(escalationBuilder.build());
        if (updatedEscalation.topic() != null && updatedEscalation.team() != null) {
            slackClient.postMessage(
                new SlackPostMessageRequest(
                    new EscalationConfirmMessage(),
                    request.messageRef().channelId(),
                    request.messageRef().actualThreadTs()
                )
            );
        }
    }

    public void openEscalation(MessageRef reference) {
        Escalation escalation = findEscalation(reference);
        if (escalation.status() != EscalationStatus.creating) {
            log.atWarn()
                .addArgument(escalation::id)
                .log("Escalation is not in creating state: {}");
            return;
        }
        if (escalation.topic() == null || escalation.team() == null) {
            log.atWarn()
                .addArgument(escalation::id)
                .log("Escalation is not ready to be opened: {}");
            return;
        }
        repository.update(
            escalation.toBuilder()
                .status(EscalationStatus.opened)
                .build()
        );

        slackClient.postMessage(new SlackPostMessageRequest(
            new EscalationConfirmedMessage(
                escalation.id(),
                escalation.topic(),
                escalation.team()
            ),
            reference.channelId(),
            reference.actualThreadTs()
        ));
    }

    public void resolveEscalation(MessageRef reference) {
        Escalation escalation = findEscalation(reference);
        if (escalation.status() != EscalationStatus.opened) {
            log.atWarn()
                .addArgument(escalation::id)
                .log("Escalation is not in opened state: {}");
            return;
        }
        repository.update(
            escalation.toBuilder()
                .status(EscalationStatus.resolved)
                .build()
        );
        slackClient.postMessage(new SlackPostMessageRequest(
            new EscalationResolvedMessage(escalation.id()),
            reference.channelId(),
            reference.actualThreadTs()
        ));
    }

    @NotNull
    private Escalation findEscalation(MessageRef reference) {
        Escalation escalation = repository.findByThreadTs(reference.actualThreadTs());
        if (escalation == null) {
            log.warn("Escalation not found by messageRef: {}", reference);
            throw new IllegalStateException("Escalation not found");
        }
        return escalation;
    }
}
