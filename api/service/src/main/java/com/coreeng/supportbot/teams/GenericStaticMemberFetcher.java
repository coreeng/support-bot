package com.coreeng.supportbot.teams;

import com.coreeng.supportbot.slack.SlackId;
import com.google.common.collect.ImmutableList;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Function;

@Slf4j
public class GenericStaticMemberFetcher<T> implements TeamMemberFetcher {
    private final @Nullable List<T> staticMembers;
    private final String teamName;
    private final Function<T, String> emailExtractor;
    private final Function<T, String> slackIdExtractor;

    public GenericStaticMemberFetcher(
            @Nullable List<T> staticMembers,
            String teamName,
            Function<T, String> emailExtractor,
            Function<T, String> slackIdExtractor) {
        this.staticMembers = staticMembers;
        this.teamName = teamName;
        this.emailExtractor = emailExtractor;
        this.slackIdExtractor = slackIdExtractor;
    }

    @Override
    public ImmutableList<TeamMember> loadInitialMembers(SlackId.Group groupId) {
        if (staticMembers == null) {
            log.debug("No static {} team members configured", teamName);
            return ImmutableList.of();
        }
        return staticMembers.stream()
                .map(member -> new TeamMember(emailExtractor.apply(member), SlackId.user(slackIdExtractor.apply(member))))
                .collect(ImmutableList.toImmutableList());
    }

    @Override
    public ImmutableList<TeamMember> handleMembershipUpdate(SlackId.Group groupId, ImmutableList<SlackId.User> teamUsers) {
        log.info("Ignoring membership update for static {} team", teamName);
        return ImmutableList.of();
    }
}
