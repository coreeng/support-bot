package com.coreeng.supportbot.teams;

import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@RequiredArgsConstructor
public class PlatformTeam {
    @EqualsAndHashCode.Include
    private final String name;

    private final Set<String> groupRefs;
    private final Set<PlatformUser> users;
}
