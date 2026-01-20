package com.coreeng.supportbot.testkit;

import static org.assertj.core.api.Assertions.assertThat;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.matching.AnythingPattern;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class EscalationMessage {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Pattern GROUP_PATTERN = Pattern.compile("\\nEscalated to team: (?<team>[^(]+)\\(<!subteam\\^(?<group>[^>]+)>\\)");

    private final String channelId;
    private final MessageTs threadTs;
    private final String teamLabel;
    private final String slackGroupId;
    private final String expectedSlackGroupId;

    public void assertMatches(SupportBotClient.TicketResponse response) {
        assertThat(response.channelId()).isEqualTo(channelId);
        assertThat(response.query().ts()).isEqualTo(threadTs);
        assertThat(response.escalated()).isTrue();
        assertThat(response.escalations())
            .anySatisfy(e -> assertThat(e.team().label()).isEqualTo(teamLabel));

        assertThat(slackGroupId).isNotBlank();
        if (expectedSlackGroupId != null && !expectedSlackGroupId.isBlank()) {
            assertThat(slackGroupId).isEqualTo(expectedSlackGroupId);
        }
    }

    public static class Receiver implements StubWithResult.Receiver<EscalationMessage> {
        private final String expectedSlackGroupId;

        public Receiver(String expectedSlackGroupId) {
            this.expectedSlackGroupId = expectedSlackGroupId;
        }
        @Override
        public MappingBuilder configureStub(MappingBuilder stubBuilder) {
            return stubBuilder
                .withFormParam("text", new AnythingPattern())
                .withFormParam("blocks", new AnythingPattern())
                .withFormParam("attachments", new AnythingPattern())
                .withFormParam("link_names", new AnythingPattern())
                .withFormParam("mrkdwn", new AnythingPattern())
                .withFormParam("unfurl_links", new AnythingPattern())
                .withFormParam("unfurl_media", new AnythingPattern())
                .withFormParam("reply_broadcast", new AnythingPattern());
        }

        @Override
        public EscalationMessage assertAndExtractResult(ServeEvent servedStub) throws IOException {
            String blocksRaw = servedStub.getRequest().formParameter("blocks").firstValue();
            // Extract team and group id from the mrkdwn text block
            String mrkdwn = objectMapper.readTree(blocksRaw).get(0).get("text").get("text").asText();
            Matcher matcher = GROUP_PATTERN.matcher(mrkdwn);
            assertThat(matcher).matches();
            String teamLabel = matcher.group("team");
            String groupId = matcher.group("group");

            String text = servedStub.getRequest().formParameter("text").firstValue();
            assertThat(text).isEqualTo("Escalation to team: " + teamLabel);

            // Validate full blocks JSON equals expected
            String expectedBlocks = String.format("""
                [
                  {
                    "type": "section",
                    "text": {
                      "type": "mrkdwn",
                      "text": "\\nEscalated to team: %s(<!subteam^%s>)"
                    }
                  }
                ]
                """, teamLabel, groupId);
            assertThatJson(objectMapper.readTree(blocksRaw)).isEqualTo(expectedBlocks);

            JsonNode responseBody = objectMapper.readTree(servedStub.getResponse().getBody());
            String channelId = responseBody.get("channel").asText();
            MessageTs threadTs = MessageTs.fromTsString(responseBody.get("message").get("thread_ts").asText());

            return EscalationMessage.builder()
                .channelId(channelId)
                .threadTs(threadTs)
                .teamLabel(teamLabel)
                .slackGroupId(groupId)
                .expectedSlackGroupId(expectedSlackGroupId)
                .build();
        }
    }
}


