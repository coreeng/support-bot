package com.coreeng.supportbot.teams.rest;

import com.google.common.collect.ImmutableList;

public record UserUI(String email, ImmutableList<TeamUI> teams) {}
