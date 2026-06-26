package com.coreeng.supportbot.teams;

import com.coreeng.supportbot.slack.SlackId;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.coreeng.supportbot.teams.groups.GroupRef;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Resolves a Slack usergroup ({@code slack:<id>}) to member emails for the PlatformTeams path, so a
 * platform team can be backed by a Slack usergroup instead of (or alongside) an IdP group.
 *
 * <p>Mirrors the support-team resolution ({@link GenericSlackMemberFetcher}): enumerate the
 * usergroup's user ids via {@code usergroups.users.list}, then look up each profile email
 * concurrently on the shared virtual-thread executor (each {@code users.info} call is blocking and
 * cached after the first hit). Members without a resolvable email are skipped — platform-team
 * membership is keyed by email, so a blank email cannot index a user.
 */
@Slf4j
@RequiredArgsConstructor
public class SlackUsersFetcher implements PlatformUsersFetcher<GroupRef.Slack> {

    private final SlackClient slackClient;
    private final ExecutorService executor;

    @Override
    public List<Membership> fetchMembershipsByGroupRef(GroupRef.Slack groupRef) {
        List<String> userIds = slackClient.getGroupMembers(SlackId.group(groupRef.id()));
        // Resolve member emails in parallel rather than serially: each getUserById is a blocking
        // Slack request, so N members would otherwise cost N round-trips on a cold profile cache.
        ExecutorCompletionService<Optional<String>> completionService = new ExecutorCompletionService<>(executor);
        for (String userId : userIds) {
            completionService.submit(() -> resolveEmail(userId, groupRef));
        }
        List<Membership> memberships = new ArrayList<>(userIds.size());
        for (int i = 0; i < userIds.size(); i++) {
            takeResult(completionService).ifPresent(email -> memberships.add(new Membership(email)));
        }
        return memberships;
    }

    private Optional<String> resolveEmail(String userId, GroupRef.Slack groupRef) {
        String email =
                slackClient.getUserById(SlackId.user(userId)).getProfile().getEmail();
        if (email == null || email.isBlank()) {
            log.atWarn()
                    .addArgument(() -> userId)
                    .addArgument(groupRef::canonical)
                    .log("Slack user {} in group {} has no email; skipping for platform-team membership");
            return Optional.empty();
        }
        return Optional.of(email);
    }

    private static Optional<String> takeResult(ExecutorCompletionService<Optional<String>> completionService) {
        try {
            return completionService.take().get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }
}
