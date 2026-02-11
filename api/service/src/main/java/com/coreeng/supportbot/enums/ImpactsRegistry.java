package com.coreeng.supportbot.enums;

import com.google.common.collect.ImmutableList;
import org.jspecify.annotations.Nullable;

public interface ImpactsRegistry {
    ImmutableList<TicketImpact> listAllImpacts();

    @Nullable TicketImpact findImpactByCode(String code);
}
