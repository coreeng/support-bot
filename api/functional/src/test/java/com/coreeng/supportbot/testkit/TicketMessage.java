package com.coreeng.supportbot.testkit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.http.FormParameter;
import com.github.tomakehurst.wiremock.matching.AnythingPattern;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import lombok.Builder;
import lombok.Getter;

import java.io.IOException;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static org.assertj.core.api.Assertions.assertThat;

@Builder
@Getter
public class TicketMessage {
    private final long ticketId;
    private final String channelId;
    private final String ts;
    private final String queryTs;

    public static class Receiver implements StubWithResult.Receiver<TicketMessage> {
        private static final ObjectMapper objectMapper = new ObjectMapper();
        private static final String textRegexp = "^Ticket Created: ID-(\\d+)$";

        @Override
        public MappingBuilder configureStub(MappingBuilder stubBuilder) {
            return stubBuilder
                .withFormParam("text", matching(textRegexp))
                .withFormParam("blocks", new AnythingPattern())
                .withFormParam("attachments", new AnythingPattern())
                .withFormParam("link_names", equalTo("0"))
                .withFormParam("mrkdwn", equalTo("1"))
                .withFormParam("unfurl_links", equalTo("0"))
                .withFormParam("unfurl_media", equalTo("0"))
                .withFormParam("reply_broadcast", equalTo("0"));
        }

        @Override
        public TicketMessage extractResult(ServeEvent servedStub) throws IOException {
            FormParameter textParam = servedStub.getRequest().formParameter("text");
            String text = textParam.firstValue();
            String ticketIdStr = text.replaceAll(textRegexp, "$1");
            long ticketId = Long.parseLong(ticketIdStr);

            FormParameter blocksParam = servedStub.getRequest().formParameter("blocks");
            FormParameter attachmentsParam = servedStub.getRequest().formParameter("attachments");
            // TODO: validate message content
            assertThat(blocksParam).isNotNull();
            assertThat(attachmentsParam).isNotNull();

            JsonNode responseBody = objectMapper.readTree(servedStub.getResponse().getBody());
            String channelId = responseBody.get("channel").asText();
            String ts = responseBody.get("ts").asText();
            String threadTs = responseBody.get("message").get("thread_ts").asText();

            return TicketMessage.builder()
                .ticketId(ticketId)
                .channelId(channelId)
                .ts(ts)
                .queryTs(threadTs)
                .build();
        }
    }
}
