package com.coreeng.supportbot.teams;

import com.google.common.collect.ImmutableList;

public record TeamDisplay(String code, String label, ImmutableList<TeamType> types, boolean active) {}
