package com.coreeng.supportbot.escalation;

import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.ticket.TicketId;
import com.coreeng.supportbot.ticket.TicketQueryService;
import com.google.common.collect.ImmutableList;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Functional-test fixture boundary for creating escalations without external Slack effects. */
@Service
@Profile({"functionaltests", "nft"})
@RequiredArgsConstructor
public class EscalationTestService {
    private final EscalationRepository escalationRepository;
    private final TicketQueryService ticketQueryService;

    @Transactional
    public boolean escalate(
            long ticketId,
            String team,
            String createdMessageTs,
            ImmutableList<String> tags,
            @Nullable EscalationSource requestedSource) {
        var id = new TicketId(ticketId);
        var ticket = ticketQueryService.findById(id);
        if (ticket == null) {
            return false;
        }
        EscalationSource source = requestedSource != null ? requestedSource : EscalationSource.manual;
        Escalation escalation = Escalation.createNew(id, team, tags, ticket.queryRef(), source).toBuilder()
                .createdMessageTs(MessageTs.of(createdMessageTs))
                .build();
        escalationRepository.createIfNotExists(escalation);
        return true;
    }
}
