package com.coreeng.supportbot.slack;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = SlackId.User.class, name = "user"),
    @JsonSubTypes.Type(value = SlackId.Bot.class, name = "bot"),
    @JsonSubTypes.Type(value = SlackId.Channel.class, name = "channel"),
    @JsonSubTypes.Type(value = SlackId.Group.class, name = "group"),
    @JsonSubTypes.Type(value = SlackId.Workflow.class, name = "workflow"),
    @JsonSubTypes.Type(value = SlackId.Subteam.class, name = "subteam")
})
public sealed interface SlackId
        permits SlackId.User, SlackId.Bot, SlackId.Channel, SlackId.Group, SlackId.Workflow, SlackId.Subteam {

    User SLACKBOT = new User("USLACKBOT");

    String id();

    static User user(String id) {
        return new User(id);
    }

    static Bot bot(String id) {
        return new Bot(id);
    }

    static Channel channel(String id) {
        return new Channel(id);
    }

    static Group group(String id) {
        return new Group(id);
    }

    static Workflow workflow(String id) {
        return new Workflow(id);
    }

    static Subteam subteam(String id) {
        return new Subteam(id);
    }

    record User(String id) implements SlackId {}

    record Bot(String id) implements SlackId {}

    record Channel(String id) implements SlackId {}

    record Group(String id) implements SlackId {}

    record Workflow(String id) implements SlackId {}

    record Subteam(String id) implements SlackId {}
}
