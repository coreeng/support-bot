package com.coreeng.supportbot.testkit;

public enum UserRole {
    tenant,
    support,
    supportBot,
    /**
     * Bot user that posts queries but doesn't have a user profile with email in Slack.
     */
    workflow
}
