package com.coreeng.supportbot.escalation;

import com.coreeng.supportbot.config.SlackEscalationProps;
import com.coreeng.supportbot.enums.EscalationTeamsRegistry;
import com.coreeng.supportbot.enums.TagsRegistry;
import com.coreeng.supportbot.slack.MessageRef;
import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.coreeng.supportbot.slack.client.SlackEditMessageRequest;
import com.coreeng.supportbot.slack.client.SlackGetMessageByTsRequest;
import com.coreeng.supportbot.slack.client.SlackPostMessageRequest;
import com.coreeng.supportbot.ticket.Ticket;
import com.coreeng.supportbot.ticket.TicketId;
import com.coreeng.supportbot.ticket.TicketQueryService;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;

import static com.google.common.base.Preconditions.checkNotNull;

@Service
@RequiredArgsConstructor
@Slf4j
public class EscalationProcessingService {
    private final EscalationRepository repository;
    private final SlackEscalationProps slackEscalationProps;
    private final EscalationCreatedMessageMapper createdMessageMapper;
    private final SlackClient slackClient;
    private final TicketQueryService ticketQueryService;
    private final EscalationTeamsRegistry escalationTeamsRegistry;
    private final TagsRegistry tagsRegistry;
    private final ApplicationEventPublisher publisher;

    public Escalation createEscalation(CreateEscalationRequest request) {
        MessageRef threadRef = null;
        if (request.threadPermalink() != null) {
            threadRef = MessageRef.fromPermalink(request.threadPermalink());
        }

        Escalation escalation = repository.createIfNotExists(
            Escalation.createNew(
                request.ticket().id(),
                threadRef,
                request.team(),
                request.tags()
            )
        );
        if (escalation == null) {
            log.warn("Escalation already exists for message: {}", threadRef);
            throw new IllegalArgumentException("Escalation already exists for the thread");
        }

        log.atInfo()
            .addArgument(escalation::id)
            .log("Escalation created: {}");

        String queryPermalink = slackClient.getPermalink(new SlackGetMessageByTsRequest(
            request.ticket().channelId(),
            request.ticket().queryTs()
        ));
        ChatPostMessageResponse postedMessage = slackClient.postMessage(new SlackPostMessageRequest(
            createdMessageMapper.renderMessage(EscalationCreatedMessage.of(
                escalation,
                checkNotNull(escalationTeamsRegistry.findEscalationTeamByName(escalation.team())).slackGroupId(),
                queryPermalink,
                tagsRegistry.listTagsByCodes(escalation.tags())
            )),
            slackEscalationProps.channelId(),
            threadRef != null ? threadRef.actualThreadTs() : null
        ));
        MessageTs postedMessageTs = MessageTs.of(postedMessage.getTs());

        if (threadRef == null) {
            escalation = escalation.toBuilder()
                .channelId(slackEscalationProps.channelId())
                .threadTs(postedMessageTs)
                .build();
        }

        escalation = repository.update(
            escalation.toBuilder()
                .createdMessageTs(postedMessageTs)
                .build()
        );

        publisher.publishEvent(new EscalationCreated(escalation.id()));
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
        Escalation updatedEscalation = repository.markResolved(escalation, Instant.now());

        Ticket ticket = checkNotNull(ticketQueryService.findById(updatedEscalation.ticketId()));
        String ticketQueryPermalink = slackClient.getPermalink(new SlackGetMessageByTsRequest(
            ticket.channelId(),
            ticket.queryTs()
        ));
        slackClient.editMessage(new SlackEditMessageRequest(
            createdMessageMapper.renderMessage(EscalationCreatedMessage.of(
                updatedEscalation,
                checkNotNull(escalationTeamsRegistry.findEscalationTeamByName(updatedEscalation.team())).slackGroupId(),
                ticketQueryPermalink,
                tagsRegistry.listTagsByCodes(escalation.tags())
            )),
            updatedEscalation.channelId(),
            updatedEscalation.createdMessageTs()
        ));
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
