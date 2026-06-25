package com.coreeng.supportbot.teams;

import com.coreeng.supportbot.slack.SlackId;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.coreeng.supportbot.teams.groups.GroupRef;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Resolves a Slack usergroup ({@code slack:<id>}) to member emails for the PlatformTeams path, so a
 * platform team can be backed by a Slack usergroup instead of (or alongside) an IdP group.
 *
 * <p>Mirrors the support-team resolution ({@link GenericSlackMemberFetcher}): enumerate the
 * usergroup's user ids via {@code usergroups.users.list}, then look up each profile email. Members
 * without a resolvable email are skipped — platform-team membership is keyed by email, so a blank
 * email cannot index a user.
 */
@Slf4j
@RequiredArgsConstructor
public class SlackUsersFetcher implements PlatformUsersFetcher<GroupRef.Slack> {

    private final SlackClient slackClient;

    @Override
    public List<Membership> fetchMembershipsByGroupRef(GroupRef.Slack groupRef) {
        List<String> userIds = slackClient.getGroupMembers(SlackId.group(groupRef.id()));
        List<Membership> memberships = new ArrayList<>(userIds.size());
        for (String userId : userIds) {
            String email =
                    slackClient.getUserById(SlackId.user(userId)).getProfile().getEmail();
            if (email == null || email.isBlank()) {
                log.atWarn()
                        .addArgument(() -> userId)
                        .addArgument(groupRef::canonical)
                        .log("Slack user {} in group {} has no email; skipping for platform-team membership");
                continue;
            }
            memberships.add(new Membership(email));
        }
        return memberships;
    }
}
