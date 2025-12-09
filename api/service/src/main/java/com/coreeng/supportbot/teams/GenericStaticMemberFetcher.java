package com.coreeng.supportbot.teams;

import com.google.common.collect.ImmutableList;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.function.Function;

@Slf4j
public class GenericStaticMemberFetcher<T> implements TeamMemberFetcher {
    private final List<T> staticMembers;
    private final String teamName;
    private final Function<T, String> emailExtractor;
    private final Function<T, String> slackIdExtractor;

    public GenericStaticMemberFetcher(
            List<T> staticMembers,
            String teamName,
            Function<T, String> emailExtractor,
            Function<T, String> slackIdExtractor) {
        this.staticMembers = staticMembers;
        this.teamName = teamName;
        this.emailExtractor = emailExtractor;
        this.slackIdExtractor = slackIdExtractor;
    }

    @Override
    public ImmutableList<TeamMember> loadInitialMembers(String groupId) {
        if (staticMembers == null) {
            log.debug("No static {} team members configured", teamName);
            return ImmutableList.of();
        }
        return staticMembers.stream()
                .map(member -> new TeamMember(emailExtractor.apply(member), slackIdExtractor.apply(member)))
                .collect(ImmutableList.toImmutableList());
    }

    @Override
    public ImmutableList<TeamMember> handleMembershipUpdate(String groupId, ImmutableList<String> teamUsers) {
        log.info("Ignoring membership update for static {} team", teamName);
        return ImmutableList.of();
    }
}

