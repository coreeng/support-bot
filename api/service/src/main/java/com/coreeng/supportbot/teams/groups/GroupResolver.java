package com.coreeng.supportbot.teams.groups;

import com.coreeng.supportbot.teams.PlatformUsersFetcher;
import com.coreeng.supportbot.teams.PlatformUsersFetcher.Membership;
import com.google.common.collect.ImmutableList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class GroupResolver {

    private final @Nullable PlatformUsersFetcher<GroupRef.Google> googleFetcher;
    private final @Nullable PlatformUsersFetcher<GroupRef.Azure> azureFetcher;
    private final @Nullable PlatformUsersFetcher<GroupRef.Static> staticFetcher;
    private final @Nullable PlatformUsersFetcher<GroupRef.Slack> slackFetcher;

    public GroupResolver(
            @Autowired(required = false) @Nullable PlatformUsersFetcher<GroupRef.Google> googleFetcher,
            @Autowired(required = false) @Nullable PlatformUsersFetcher<GroupRef.Azure> azureFetcher,
            @Autowired(required = false) @Nullable PlatformUsersFetcher<GroupRef.Static> staticFetcher,
            @Autowired(required = false) @Nullable PlatformUsersFetcher<GroupRef.Slack> slackFetcher) {
        this.googleFetcher = googleFetcher;
        this.azureFetcher = azureFetcher;
        this.staticFetcher = staticFetcher;
        this.slackFetcher = slackFetcher;
    }

    public List<Membership> resolveMembers(GroupRef ref) {
        return switch (ref) {
            case GroupRef.Slack s -> tryFetch(slackFetcher, s);
            case GroupRef.Google g -> tryFetch(googleFetcher, g);
            case GroupRef.Azure a -> tryFetch(azureFetcher, a);
            case GroupRef.Static st -> tryFetch(staticFetcher, st);
            case GroupRef.Jwt j -> emptyForJwt(j);
        };
    }

    private static <R extends GroupRef> List<Membership> tryFetch(@Nullable PlatformUsersFetcher<R> fetcher, R ref) {
        if (fetcher == null) {
            log.warn("No PlatformUsersFetcher registered for {}", ref.canonical());
            return ImmutableList.of();
        }
        return fetcher.fetchMembershipsByGroupRef(ref);
    }

    private static List<Membership> emptyForJwt(GroupRef.Jwt ref) {
        log.atDebug()
                .addArgument(ref::canonical)
                .log("JWT group {} cannot enumerate members; resolution is per-request via JwtGroupTeamMerger");
        return ImmutableList.of();
    }
}
