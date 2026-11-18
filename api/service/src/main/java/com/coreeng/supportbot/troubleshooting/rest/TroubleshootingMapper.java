package com.coreeng.supportbot.troubleshooting.rest;

import com.coreeng.supportbot.troubleshooting.TroubleshootingEvent;
import com.coreeng.supportbot.troubleshooting.TroubleshootingService.SessionWithEvents;
import com.google.common.collect.ImmutableList;

final class TroubleshootingMapper {
    private TroubleshootingMapper() {}

    static TroubleshootingEventUI toUI(TroubleshootingEvent event) {
        return new TroubleshootingEventUI(
                event.seq(), event.type().name(), event.actor(), event.payload(), event.createdAt());
    }

    static TroubleshootingSessionUI toUI(SessionWithEvents session) {
        ImmutableList<TroubleshootingEventUI> events =
                session.events().stream().map(TroubleshootingMapper::toUI).collect(ImmutableList.toImmutableList());
        long lastSeq = events.isEmpty() ? 0 : events.getLast().seq();
        return new TroubleshootingSessionUI(
                session.session().id(),
                session.session().ticketId(),
                session.session().mode().name(),
                lastSeq,
                session.session().createdAt(),
                events);
    }
}
