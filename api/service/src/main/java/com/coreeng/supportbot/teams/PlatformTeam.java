package com.coreeng.supportbot.teams;

import com.coreeng.supportbot.teams.groups.GroupRef;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class PlatformTeam {
    @EqualsAndHashCode.Include
    private final String name;

    private final String code;
    private final Set<GroupRef> groupRefs;
    private final Set<PlatformUser> users;

    public PlatformTeam(String name, String code, Set<GroupRef> groupRefs, Set<PlatformUser> users) {
        this.name = name;
        this.code = code;
        this.groupRefs = groupRefs;
        this.users = users;
    }

    /** Convenience for scraped teams whose code is the same as the name (the default). */
    public PlatformTeam(String name, Set<GroupRef> groupRefs, Set<PlatformUser> users) {
        this(name, name, groupRefs, users);
    }
}
