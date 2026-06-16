package com.coreeng.supportbot.teams;

import com.google.common.collect.ImmutableList;

/** Display-safe team resolution: always has a label; {@code active=false} means retired/unknown. */
public record TeamDisplay(String code, String label, ImmutableList<TeamType> types, boolean active) {}
