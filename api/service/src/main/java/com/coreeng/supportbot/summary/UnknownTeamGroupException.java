package com.coreeng.supportbot.summary;

/**
 * The caller asked for a team group that does not exist (any more) — typically a stale shared link.
 * Reported as a 400 with its own code, so the page can tell it apart from a window error and
 * refresh its list of groups.
 */
public class UnknownTeamGroupException extends RuntimeException {

    private final String teamGroup;

    public UnknownTeamGroupException(String teamGroup) {
        super("No team group has the code '" + teamGroup + "'");
        this.teamGroup = teamGroup;
    }

    public String teamGroup() {
        return teamGroup;
    }
}
