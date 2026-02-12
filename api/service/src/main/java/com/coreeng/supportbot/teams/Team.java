package com.coreeng.supportbot.teams;

import com.google.common.collect.ImmutableList;

public record Team(String label, String code, ImmutableList<TeamType> types) {}
