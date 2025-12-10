package com.coreeng.supportbot.rbac;

import com.coreeng.supportbot.slack.SlackId;
import com.coreeng.supportbot.teams.SupportTeamService;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class RbacService {
    private final RbacProps rbacProps;
    private final SupportTeamService supportTeamService;

    public boolean isEnabled() {
        return rbacProps.enabled();
    }

    public boolean isSupportBySlackId(SlackId.User slackId) {
        if (!isEnabled()) {
            return true;
        }
        return supportTeamService.isMemberByUserId(slackId);
    }

    public boolean isSupportByEmail(String email) {
        if (!isEnabled()) {
            return true;
        }
        return supportTeamService.isMemberByUserEmail(email);
    }
}