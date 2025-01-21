package com.coreeng.supportbot.teams;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Set;

@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@RequiredArgsConstructor
public class PlatformUser {
    @EqualsAndHashCode.Include
    private final String email;
    private final Set<PlatformTeam> teams;
}

