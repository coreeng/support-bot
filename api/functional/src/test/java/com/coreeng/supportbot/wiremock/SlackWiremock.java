package com.coreeng.supportbot.wiremock;

import com.coreeng.supportbot.Config;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.matching.AnythingPattern;
import org.apache.commons.text.StringSubstitutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.Charset;
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

    public void stubReactionAdded() {
        givenThat(post("/api/reactions.add")
            .withFormParam("name", equalTo("ticket"))
            .withFormParam("channel", equalTo("C08CC3MCBKN"))
            .withFormParam("timestamp", new AnythingPattern())
            .willReturn(okJson("""
                                {"ok":true}""")));
    }

    public void stubPostMessage() {
        givenThat(post("/api/chat.postMessage")
            .withFormParam("channel", equalTo("C08CC3MCBKN"))
            .withFormParam("thread_ts", new AnythingPattern())
            .withFormParam("text", equalTo(URLEncoder.encode("Ticket Created", Charset.defaultCharset())))
            .withFormParam("link_names", equalTo("0"))
            .withFormParam("mrkdwn", equalTo("1"))
            .withFormParam("blocks", equalTo("""
                %5B%7B%22type%22%3A%22section%22%2C%22text%22%3A%7B%22type%22%3A%22mrkdwn%22%2C%22text%22%3A%22*Ticket%20Created*%3A%20%60ID-4%60%22%7D%7D%5D
                """))
            .withFormParam("attachments", equalTo("""
                %5B%7B%22type%22%3A%22section%22%2C%22text%22%3A%7B%22type%22%3A%22mrkdwn%22%2C%22text%22%3A%22*Ticket%20Created*%3A%20%60ID-4%60%22%7D%7D%5D
                """))
            .withFormParam("unfurl_media", equalTo("0"))
            .withFormParam("reply_broadcast", equalTo("0"))
            .willReturn(okJson("""
                {"ok":true,"channel":"C08CC3MCBKN","ts":"1751299905.620089","message":{"user":"U08C2T9M6NA","type":"message","ts":"1751299905.620089","bot_id":"B08CYAPQFL0","app_id":"A08C6KMK0JH","text":"Ticket Created: ID-4","team":"T014GGGRDUK","bot_profile":{"id":"B08CYAPQFL0","app_id":"A08C6KMK0JH","user_id":"U08C2T9M6NA","name":"Core Support Bot (local \\u2013 Ilia)","icons":{"image_36":"https:\\/\\/a.slack-edge.com\\/80588\\/img\\/plugins\\/app\\/bot_36.png","image_48":"https:\\/\\/a.slack-edge.com\\/80588\\/img\\/plugins\\/app\\/bot_48.png","image_72":"https:\\/\\/a.slack-edge.com\\/80588\\/img\\/plugins\\/app\\/service_72.png"},"deleted":false,"updated":1738932177,"team_id":"T014GGGRDUK"},"thread_ts":"1751299902.155899","parent_user_id":"U069PNNUUBB","attachments":[{"id":1,"blocks":[{"type":"divider","block_id":"8eVzx"},{"type":"section","block_id":"PdBMY","text":{"type":"plain_text","text":"Opened: Today at 18:11","emoji":true}},{"type":"actions","block_id":"2oxVs","elements":[{"type":"button","action_id":"ticket-summary-view","text":{"type":"plain_text","text":"Full Summary","emoji":true},"value":"{\\"ticketId\\":4}"},{"type":"button","action_id":"ticket-escalate","text":{"type":"plain_text","text":"Escalate","emoji":true},"value":"{\\"ticketId\\":4}"}]},{"type":"context","block_id":"a6Za6","elements":[{"type":"plain_text","text":":pushpin: Options above supplied for Support engineers. Please ignore...","emoji":true}]}],"color":"#00ff00","fallback":"Opened: Today at 18:11"}],"blocks":[{"type":"section","block_id":"YfM6R","text":{"type":"mrkdwn","text":"*Ticket Created*: `ID-4`","verbatim":false}}]}}
                """)));

    }

    private void setupAppInitMocks() {
        logger.info("Setting up initial Slack API stubs");
        givenThat(post("/api/auth.test")
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
                        "last_name": "${lastName}"
                      }
                    }"""))));

        }
    }
}
