package com.coreeng.supportbot.enums;

import com.google.common.collect.ImmutableList;
import org.jspecify.annotations.Nullable;

public interface ImpactsRegistry {
    ImmutableList<TicketImpact> listAllImpacts();

    /** All impacts including soft-deleted (retired) — for display/badging, not for pickers (PT-518). */
    ImmutableList<TicketImpact> listAllImpactsIncludingRetired();

    @Nullable TicketImpact findImpactByCode(String code);
}
