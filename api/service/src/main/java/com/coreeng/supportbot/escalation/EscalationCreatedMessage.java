package com.coreeng.supportbot.escalation;



public record EscalationCreatedMessage(
    EscalationId escalationId,
    String escalationTeam,
    String slackTeamGroupId
) {
    public static EscalationCreatedMessage of(
        Escalation escalation,
        String slackTeamGroupId
    ) {
        return new EscalationCreatedMessage(
            escalation.id(),
            escalation.team(),
            slackTeamGroupId
        );
    }
}
