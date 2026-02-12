package com.coreeng.supportbot.slack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.coreeng.supportbot.util.JsonMapper;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SlackIdTest {
    private final JsonMapper jsonMapper = new JsonMapper();

    static Stream<Arguments> slackIdTypes() {
        return Stream.of(
                Arguments.of(SlackId.user("U12345"), "user", SlackId.User.class),
                Arguments.of(SlackId.bot("B12345"), "bot", SlackId.Bot.class),
                Arguments.of(SlackId.channel("C12345"), "channel", SlackId.Channel.class),
                Arguments.of(SlackId.group("G12345"), "group", SlackId.Group.class),
                Arguments.of(SlackId.workflow("W12345"), "workflow", SlackId.Workflow.class),
                Arguments.of(SlackId.subteam("S12345"), "subteam", SlackId.Subteam.class));
    }

    @ParameterizedTest
    @MethodSource("slackIdTypes")
    void serialization_includesTypeDiscriminator(SlackId slackId, String expectedType, Class<?> expectedClass) {
        String json = jsonMapper.toJsonString(slackId);

        String expectedJson = "{\"type\":\"" + expectedType + "\",\"id\":\"" + slackId.id() + "\"}";
        assertEquals(expectedJson, json);
    }

    @ParameterizedTest
    @MethodSource("slackIdTypes")
    void deserialization_restoresCorrectType(SlackId slackId, String typeName, Class<?> expectedClass) {
        String json = "{\"type\":\"" + typeName + "\",\"id\":\"" + slackId.id() + "\"}";

        SlackId deserialized = jsonMapper.fromJsonString(json, SlackId.class);

        assertInstanceOf(expectedClass, deserialized);
        assertEquals(slackId.id(), deserialized.id());
        assertEquals(slackId, deserialized);
    }

    @Test
    void roundTrip_user() {
        SlackId.User original = SlackId.user("U12345ABC");

        String json = jsonMapper.toJsonString(original);
        SlackId deserialized = jsonMapper.fromJsonString(json, SlackId.class);

        assertInstanceOf(SlackId.User.class, deserialized);
        assertEquals(original, deserialized);
    }

    @Test
    void roundTrip_bot() {
        SlackId.Bot original = SlackId.bot("B98765XYZ");

        String json = jsonMapper.toJsonString(original);
        SlackId deserialized = jsonMapper.fromJsonString(json, SlackId.class);

        assertInstanceOf(SlackId.Bot.class, deserialized);
        assertEquals(original, deserialized);
    }

    @Test
    void factoryMethods_returnCorrectTypes() {
        assertInstanceOf(SlackId.User.class, SlackId.user("U123"));
        assertInstanceOf(SlackId.Bot.class, SlackId.bot("B123"));
        assertInstanceOf(SlackId.Channel.class, SlackId.channel("C123"));
        assertInstanceOf(SlackId.Group.class, SlackId.group("G123"));
        assertInstanceOf(SlackId.Workflow.class, SlackId.workflow("W123"));
        assertInstanceOf(SlackId.Subteam.class, SlackId.subteam("S123"));
    }
}
