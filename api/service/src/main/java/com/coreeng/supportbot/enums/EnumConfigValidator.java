package com.coreeng.supportbot.enums;

import com.coreeng.supportbot.config.EnumProps;
import com.coreeng.supportbot.config.EnumerationValue;
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
@Order(101)
@RequiredArgsConstructor
@Slf4j
public class EnumConfigValidator implements ApplicationRunner {

    private final EnumProps enumProps;

    @Override
    public void run(ApplicationArguments args) {
        List<String> problems = new ArrayList<>();
        problems.addAll(findProblems("enums.escalation-teams", enumProps.escalationTeams()));
        problems.addAll(findProblems("enums.tags", enumProps.tags()));
        problems.addAll(findProblems("enums.impacts", enumProps.impacts()));

        if (!problems.isEmpty()) {
            throw new IllegalStateException("Invalid enum configuration: " + String.join("; ", problems)
                    + ". Codes are immutable primary keys; fix the duplicate/blank codes in config.");
        }
        log.atInfo().log("Enum config validation passed");
    }

    private static List<String> findProblems(String path, ImmutableList<? extends EnumerationValue> values) {
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
}
