package com.coreeng.supportbot.slack;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public sealed interface SlackId
        permits SlackId.User, SlackId.Bot, SlackId.Channel, SlackId.Group, SlackId.Workflow, SlackId.Subteam {

    @JsonValue
    String id();

    @JsonCreator
    static SlackId of(String id) {
        return switch (id.charAt(0)) {
            case 'U' -> new User(id);
            case 'B' -> new Bot(id);
            case 'C' -> new Channel(id);
            case 'G' -> new Group(id);
            case 'W' -> new Workflow(id);
            case 'S' -> new Subteam(id);
            default -> throw new IllegalArgumentException("Unknown Slack id type: " + id);
        };
    }

    record User(String id) implements SlackId {}
    record Bot(String id) implements SlackId {}
    record Channel(String id) implements SlackId {}
    record Group(String id) implements SlackId {}
    record Workflow(String id) implements SlackId {}
    record Subteam(String id) implements SlackId {}
}
