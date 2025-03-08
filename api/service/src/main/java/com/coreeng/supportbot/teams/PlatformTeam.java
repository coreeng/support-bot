package com.coreeng.supportbot.teams;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Set;

@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@RequiredArgsConstructor
public class PlatformTeam {
    @EqualsAndHashCode.Include
    private final String name;
    private final Set<String> groupRefs;
    private final Set<PlatformUser> users;
}
