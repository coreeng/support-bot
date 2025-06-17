package com.coreeng.supportbot.wiremock;

import com.coreeng.supportbot.Config;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.apache.commons.text.StringSubstitutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.stream.Collectors;

import static com.github.tomakehurst.wiremock.client.WireMock.*;


/**
 * Wiremock implementation for Slack service.
 * Handles mocking of Slack API endpoints.
 */
public class SlackWiremock extends WireMockServer {
    private static final Logger logger = LoggerFactory.getLogger(SlackWiremock.class);

    private final Config.SlackMock config;

    public SlackWiremock(Config.SlackMock config) {
        super(WireMockConfiguration.options()
            .port(config.port()));
        this.config = config;
    }

    @Override
    public void start() {
        super.start();
        setupAppInitMocks();
        logger.info("Started Slack Wiremock server on port {}", this.port());
    }

    @Override
    public void stop() {
        super.stop();
        logger.info("Stopped Slack Wiremock server");
    }

    private void setupAppInitMocks() {
        logger.info("Setting up initial Slack API stubs");
        givenThat(get("/api/auth.test")
            .willReturn(okJson(new StringSubstitutor(Map.of(
                "url", config.serverUrl(),
                "team", config.team(),
                "teamId", config.teamId(),
                "userId", config.userId(),
                "botId", config.botId()
            )).replace("""
                {
                  "ok":true,
                  "url":"${url}",
                  "team":"${team}",
                  "user":"core_support",
                  "team_id":"${teamId}",
                  "user_id":"${userId}",
                  "bot_id":"${botId}",
                  "is_enterprise_install":false}"""))));

        // list support members
        givenThat(post("/api/usergroups.users.list")
            .withFormParam("usergroup", equalTo(config.supportGroupId()))
            .withFormParam("include_disabled", equalTo("0"))
            .willReturn(okJson(new StringSubstitutor(Map.of(
                "supportMembers", config.supportMembers().stream()
                    .map(member -> "\"" + member.userId() + "\"")
                    .collect(Collectors.joining(","))
            ))
                .replace("""
                    {
                      "ok": true,
                      "users": [
                        ${supportMembers}
                      ]
                    }"""))));
        for (Config.SlackSupportMember supportMember : config.supportMembers()) {
            String[] nameParts = supportMember.name().split(" ");
            givenThat(post("/api/users.profile.get")
                .withFormParam("user", equalTo(supportMember.userId()))
                .withFormParam("include_labels", equalTo("0"))
                .willReturn(okJson(new StringSubstitutor(Map.of(
                    "name", supportMember.name(),
                    "firstName", nameParts[0],
                    "lastName", nameParts[1],
                    "email", supportMember.email()
                )).replace("""
                    {
                      "ok": true,
                      "profile": {
                        "real_name": "${name}",
                        "real_name_normalized": "${name}",
                        "avatar_hash": "g54bfb2943ca",
                        "email": "${email}",
                        "huddle_state": "default_unset",
                        "first_name": "${firstName}",
                        "last_name": "${lastName}",
                      }
                    }"""))));

        }
    }
}
