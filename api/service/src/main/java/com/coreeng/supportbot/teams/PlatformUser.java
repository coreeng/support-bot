package com.coreeng.supportbot.teams;

import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@RequiredArgsConstructor
public class PlatformUser {
    @EqualsAndHashCode.Include
    private final String email;

    private final Set<PlatformTeam> teams;
}
