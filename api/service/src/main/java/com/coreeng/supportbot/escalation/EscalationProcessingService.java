package com.coreeng.supportbot.escalation;

import static com.google.common.base.Preconditions.checkNotNull;

import com.coreeng.supportbot.config.SlackTicketsProps;
import com.coreeng.supportbot.enums.EscalationTeamsRegistry;
import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.coreeng.supportbot.slack.client.SlackPostMessageRequest;
import com.coreeng.supportbot.ticket.TicketId;
import java.time.Instant;

import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EscalationProcessingService {
    private final EscalationRepository repository;
    private final EscalationCreatedMessageMapper createdMessageMapper;
    private final SlackClient slackClient;
    private final EscalationTeamsRegistry escalationTeamsRegistry;
    private final SlackTicketsProps slackTicketsProps;

    public Escalation createEscalation(CreateEscalationRequest request) {
        Escalation escalation = repository.createIfNotExists(
            Escalation.createNew(
                request.ticket().id(),
                request.team(),
                request.tags()
            )
        );
        if (escalation.id() == null) {
            log.warn("Escalation already exists");
            return escalation;
        }

        log.atInfo()
            .addArgument(escalation::id)
            .log("Escalation created: {}");

        ChatPostMessageResponse messagePostResponse = slackClient.postMessage(new SlackPostMessageRequest(
            createdMessageMapper.renderMessage(EscalationCreatedMessage.of(
                escalation,
                checkNotNull(escalationTeamsRegistry.findEscalationTeamByName(escalation.team())).slackGroupId()
            )),
            slackTicketsProps.channelId(),
            request.ticket().queryTs()
        ));

        escalation = escalation.toBuilder()
            .channelId(request.ticket().channelId())
            .threadTs(request.ticket().queryTs())
            .createdMessageTs(MessageTs.of(messagePostResponse.getTs()))
            .build();
        escalation = repository.update(escalation);

        return escalation;
    }

    public void resolve(EscalationId id) {
        Escalation escalation = findEscalation(id);
        if (escalation.status() != EscalationStatus.opened) {
            log.atWarn()
                .addArgument(escalation::id)
                .log("Escalation is already resolved: {}");
            return;
        }
        resolve(escalation);
    }

    public void resolveByTicketId(TicketId ticketId) {
        for (var escalation : repository.listByTicketId(ticketId)) {
            if (EscalationStatus.resolved.equals(escalation.status())) {
                continue;
            }
            resolve(escalation);
        }
    }

    private void resolve(Escalation escalation) {
        repository.markResolved(escalation, Instant.now());
    }

    @NotNull
    private Escalation findEscalation(EscalationId id) {
        Escalation escalation = repository.findById(id);
        if (escalation == null) {
            log.warn("Escalation not found by id: {}", id);
            throw new IllegalArgumentException("Escalation not found");
        }
        return escalation;
    }
}
