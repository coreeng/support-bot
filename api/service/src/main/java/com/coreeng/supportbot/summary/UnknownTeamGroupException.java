package com.coreeng.supportbot.summary;

/**
 * The caller asked for a team group no longer (or never) configured — typically a stale shared
 * link. Reported as a 400 with its own code, so the page can drop the parameter instead of showing
 * a window error.
 */
public class UnknownTeamGroupException extends RuntimeException {

    private final String teamGroup;

    public UnknownTeamGroupException(String teamGroup) {
        super("No team group is configured with code '" + teamGroup + "'");
        this.teamGroup = teamGroup;
    }

    public String teamGroup() {
        return teamGroup;
    }
}
