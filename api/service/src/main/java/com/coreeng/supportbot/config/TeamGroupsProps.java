package com.coreeng.supportbot.config;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Team groups: named sets of tenant teams — a client's business area, domain or tribe — that the
 * Support Summary page can be focused on (ADR-009).
 *
 * <p>Groups are keyed on team codes rather than on how teams are discovered, so they work the same
 * whichever team provider a deployment uses. No groups (the default) hides the selector.
 *
 * <p>Structural mistakes fail startup here; a team code that does not match a current tenant team
 * only warns (see {@code TeamGroupService}), because retired codes legitimately stay on historical
 * tickets.
 *
 * @param displayName the noun the UI shows for a group, e.g. "Area", "Domain", "Tribe"
 * @param groups the configured groups, in display order
 */
@ConfigurationProperties("team-groups")
public record TeamGroupsProps(
        @DefaultValue("Area") String displayName,
        @DefaultValue List<Group> groups) {

    /** URL-safe, so a code can sit in a shareable link without encoding. */
    public static final Pattern CODE_PATTERN = Pattern.compile("^[a-z0-9]([a-z0-9-]{0,62}[a-z0-9])?$");

    public TeamGroupsProps {
        if (displayName.isBlank()) {
            throw new IllegalArgumentException("team-groups.display-name must not be blank");
        }
        Set<String> codes = new HashSet<>();
        for (Group group : groups) {
            if (!codes.add(group.code())) {
                throw new IllegalArgumentException(
                        "team-groups.groups has more than one group with code '" + group.code() + "'");
            }
        }
        groups = List.copyOf(groups);
    }

    /**
     * @param code stable identifier, used in the URL; lowercase letters, digits and hyphens
     * @param label the name shown in the UI
     * @param teamCodes the tenant team codes ({@code ticket.team}) the group covers; a team may
     *     belong to more than one group
     */
    public record Group(String code, String label, List<String> teamCodes) {

        public Group {
            if (code == null || !CODE_PATTERN.matcher(code).matches()) {
                throw new IllegalArgumentException("team-groups.groups[].code must be lowercase letters, digits and"
                        + " hyphens (not starting or ending with a hyphen, at most 64 characters), got: '" + code
                        + "'");
            }
            if (label == null || label.isBlank()) {
                throw new IllegalArgumentException("team-groups.groups[" + code + "].label must not be blank");
            }
            if (teamCodes == null || teamCodes.isEmpty()) {
                throw new IllegalArgumentException(
                        "team-groups.groups[" + code + "].team-codes must list at least one team code");
            }
            Set<String> seen = new HashSet<>();
            for (String teamCode : teamCodes) {
                if (teamCode == null || teamCode.isBlank()) {
                    throw new IllegalArgumentException(
                            "team-groups.groups[" + code + "].team-codes must not contain a blank entry");
                }
                if (!seen.add(teamCode)) {
                    throw new IllegalArgumentException(
                            "team-groups.groups[" + code + "].team-codes lists '" + teamCode + "' more than once");
                }
            }
            teamCodes = List.copyOf(teamCodes);
        }
    }
}
