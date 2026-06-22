package com.coreeng.supportbot.enums;

import com.coreeng.supportbot.config.EnumProps;
import com.coreeng.supportbot.config.EnumerationValue;
import com.coreeng.supportbot.teams.StaticPlatformTeamsProps;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(99)
@RequiredArgsConstructor
@Slf4j
public class EnumConfigValidator implements ApplicationRunner {

    private final EnumProps enumProps;
    private final StaticPlatformTeamsProps staticTeamsProps;

    @Override
    public void run(ApplicationArguments args) {
        List<String> problems = new ArrayList<>();
        problems.addAll(validateCodes("enums.escalation-teams", enumProps.escalationTeams()));
        problems.addAll(validateCodes("enums.tags", enumProps.tags()));
        problems.addAll(validateCodes("enums.impacts", enumProps.impacts()));
        if (staticTeamsProps.enabled()) {
            problems.addAll(validateStaticTeamCodes(staticTeamsProps.teams()));
        }

        if (!problems.isEmpty()) {
            throw new IllegalStateException("Invalid enum configuration: " + String.join("; ", problems)
                    + ". Codes are immutable primary keys; fix the duplicate/blank codes in config.");
        }
        log.atInfo().log("Enum config validation passed");
    }

    private static List<String> validateCodes(String path, ImmutableList<? extends EnumerationValue> values) {
        List<String> problems = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (EnumerationValue value : values) {
            String code = value.code();
            if (code == null || code.isBlank()) {
                problems.add(path + " has an entry with a blank code");
            } else if (!seen.add(code)) {
                problems.add(path + " has duplicate code '" + code + "'");
            }
        }
        return problems;
    }

    private static List<String> validateStaticTeamCodes(List<StaticPlatformTeamsProps.TeamConfig> teams) {
        List<String> problems = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (StaticPlatformTeamsProps.TeamConfig team : teams) {
            String explicit = team.code();
            String code = explicit != null && !explicit.isBlank() ? explicit : team.name();
            if (code.isBlank()) {
                problems.add("platform-integration.teams-scraping.static.teams has an entry with a blank code/name");
            } else if (!seen.add(code)) {
                problems.add("platform-integration.teams-scraping.static.teams has duplicate code '" + code + "'");
            }
        }
        return problems;
    }
}
