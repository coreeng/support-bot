package com.coreeng.supportbot.enums;

import com.coreeng.supportbot.config.EnumerationValue;

public record EscalationTeam(String label, String code, String slackGroupId) implements EnumerationValue {}
