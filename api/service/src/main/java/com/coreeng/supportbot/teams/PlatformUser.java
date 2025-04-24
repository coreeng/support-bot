package com.coreeng.supportbot.teams;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

import java.util.Set;

@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@RequiredArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
public class PlatformUser {
    @EqualsAndHashCode.Include
    private final String email;
    private final Set<PlatformTeam> teams;
}

