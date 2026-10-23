package com.coreeng.supportbot.teamgroups;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.ImmutableSortedSet;
import java.util.regex.Pattern;

/**
 * A named set of tenant teams — a business area, domain or tribe — that the Support
 * Summary can be focused on. Not to be confused with {@code teams.groups.GroupRef}, which is an
 * identity-provider group that a team's members are resolved from.
 *
 * @param code identifier derived from the label, used in the URL; matches {@link #CODE_PATTERN}
 * @param label the name shown in the UI; not blank
 * @param teamCodes the tenant team codes ({@code ticket.team}) the group covers; not empty, and
 *     sorted so the membership digest does not depend on the order teams were fetched in
 */
public record TeamGroup(String code, String label, ImmutableSortedSet<String> teamCodes) {

    /** Longest code, so it stays short in a shareable link and in {@code summary_snapshot.team_group}. */
    public static final int MAX_CODE_LENGTH = 64;

    /**
     * Lowercase ASCII letters, digits and inner hyphens, so a code sits in a link without encoding. Never
     * empty: the whole window is stored under {@code ''}, and a group must not share its key.
     */
    public static final Pattern CODE_PATTERN =
            Pattern.compile("^[a-z0-9]([a-z0-9-]{0," + (MAX_CODE_LENGTH - 2) + "}[a-z0-9])?$");

    public TeamGroup {
        checkArgument(CODE_PATTERN.matcher(code).matches(), "Team group code '%s' is not URL-safe", code);
        checkArgument(!label.isBlank(), "Team group '%s' has a blank label", code);
        checkArgument(!teamCodes.isEmpty(), "Team group '%s' has no teams", code);
    }
}
