package com.coreeng.supportbot.config;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Team groups: named sets of tenant teams — a business area, domain or tribe — that the Support
 * Summary page can be focused on (ADR-009).
 *
 * <p>Groups are built from a team attribute: every tenant team carrying the attribute joins the
 * group named by its value. Attributes come from the team provider, either the k8s-generic
 * fetcher's expressions or a static team's config. No attribute (the default) means no groups,
 * which hides the selector.
 *
 * @param displayName the noun the UI shows for a group, e.g. "Area", "Domain", "Tribe"
 * @param attribute the name of the team attribute whose value names a team's group, with
 *     surrounding whitespace removed; null for none
 */
@ConfigurationProperties("team-groups")
public record TeamGroupsProps(
        @DefaultValue("Area") String displayName, @Nullable String attribute) {

    public TeamGroupsProps {
        if (displayName.isBlank()) {
            throw new IllegalArgumentException("team-groups.display-name must not be blank");
        }
        if (attribute != null) {
            if (attribute.isBlank()) {
                throw new IllegalArgumentException(
                        "team-groups.attribute must not be blank; remove it to turn groups off");
            }
            // Padding would never match an attribute name, and silently mean no groups.
            attribute = attribute.strip();
        }
    }
}
