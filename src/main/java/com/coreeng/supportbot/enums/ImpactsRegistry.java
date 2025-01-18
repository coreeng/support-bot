package com.coreeng.supportbot.enums;

import com.google.common.collect.ImmutableList;

import javax.annotation.Nullable;

public interface ImpactsRegistry {
    ImmutableList<TicketImpact> listAllImpacts();

    @Nullable
    TicketImpact findImpactByCode(String code);
}
