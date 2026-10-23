package com.coreeng.supportbot.teamgroups;

import static java.util.Objects.requireNonNull;

import com.coreeng.supportbot.config.TeamGroupsProps;
import com.coreeng.supportbot.teams.Team;
import com.coreeng.supportbot.teams.TeamService;
import com.coreeng.supportbot.teams.TeamType;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.event.Level;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Team groups built from a team attribute ({@code team-groups.attribute}): each distinct value is a
 * group, labelled with the value, whose members are the tenant teams carrying it.
 *
 * <p>Membership is the teams' current attributes, so a team that is no longer fetched leaves its
 * group, taking its tickets out of the group's summary, and a team that moves takes its history
 * with it. Teams are fetched once at startup, so the groups are built once, when the application
 * is ready, which is also when a misconfigured attribute is logged.
 */
@Component
@Slf4j
public class AttributeTeamGroupProvider implements TeamGroupProvider {

    private final TeamGroupsProps props;
    private final TeamService teamService;
    private final Supplier<ImmutableList<TeamGroup>> groups;

    public AttributeTeamGroupProvider(TeamGroupsProps props, TeamService teamService) {
        this.props = props;
        this.teamService = teamService;
        this.groups = Suppliers.memoize(this::build);
    }

    @Override
    public ImmutableList<TeamGroup> groups() {
        return groups.get();
    }

    /** Builds the groups up front, so their log lines land in the startup log rather than on a first visit. */
    @EventListener(ApplicationReadyEvent.class)
    public void buildAtStartup() {
        groups();
    }

    private ImmutableList<TeamGroup> build() {
        String attribute = props.attribute();
        if (attribute == null) {
            return ImmutableList.of();
        }
        ImmutableList<Team> teams = teamService.listTeamsByType(TeamType.TENANT);

        // Values that differ only in case, accents, or the spaces and punctuation between words share
        // a code, and so a group.
        Map<String, SortedSet<String>> labelsByCode = new TreeMap<>();
        Map<String, ImmutableSortedSet.Builder<String>> membersByCode = new TreeMap<>();
        SortedSet<String> withoutValue = new TreeSet<>();
        for (Team team : teams) {
            String value = team.attributes().get(attribute);
            String label = value == null ? "" : value.strip();
            String code = toCode(label);
            if (code.isEmpty()) {
                if (!label.isEmpty()) {
                    log.atWarn()
                            .addKeyValue("team", team.code())
                            .log(
                                    "Team attribute '{}' has no ASCII letters or digits to name a group by; leaving"
                                            + " the team out of every group",
                                    attribute);
                }
                withoutValue.add(team.code());
                continue;
            }
            labelsByCode.computeIfAbsent(code, k -> new TreeSet<>()).add(label);
            membersByCode
                    .computeIfAbsent(code, k -> ImmutableSortedSet.naturalOrder())
                    .add(team.code());
        }

        ImmutableList<TeamGroup> built = membersByCode.entrySet().stream()
                .map(entry -> new TeamGroup(
                        entry.getKey(),
                        label(entry.getKey(), requireNonNull(labelsByCode.get(entry.getKey()))),
                        entry.getValue().build()))
                .sorted(Comparator.comparing(TeamGroup::label, String.CASE_INSENSITIVE_ORDER))
                .collect(ImmutableList.toImmutableList());

        log.atLevel(built.isEmpty() && !teams.isEmpty() ? Level.WARN : Level.INFO)
                .log(
                        "Team groups from attribute '{}': {} group(s) over {} of {} tenant team(s)",
                        attribute,
                        built.size(),
                        teams.size() - withoutValue.size(),
                        teams.size());
        if (!withoutValue.isEmpty()) {
            log.info("Tenant teams in no group (no usable value for '{}'): {}", attribute, withoutValue);
        }
        return built;
    }

    /**
     * The first spelling in sort order (by character code, so "Ops" before "ops") labels the group;
     * the others are logged so they can be made consistent.
     */
    private String label(String code, SortedSet<String> labels) {
        if (labels.size() > 1) {
            log.atWarn()
                    .addKeyValue("group", code)
                    .log(
                            "Team attribute '{}' spells one group several ways {}; labelling it '{}'",
                            props.attribute(),
                            labels,
                            labels.first());
        }
        return labels.first();
    }

    /**
     * Lowercase ASCII letters, digits and single hyphens, at most {@value TeamGroup#MAX_CODE_LENGTH}
     * characters, so it matches {@link TeamGroup#CODE_PATTERN}. Accents are removed first; any other
     * run of characters becomes one hyphen, so letters outside ASCII do not count. Empty when nothing
     * is left.
     */
    static String toCode(String value) {
        String ascii = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String code = ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        code = stripHyphens(code);
        return code.length() <= TeamGroup.MAX_CODE_LENGTH
                ? code
                : stripHyphens(code.substring(0, TeamGroup.MAX_CODE_LENGTH));
    }

    private static String stripHyphens(String code) {
        return code.replaceAll("^-+|-+$", "");
    }
}
