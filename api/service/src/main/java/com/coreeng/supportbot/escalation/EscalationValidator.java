package com.coreeng.supportbot.escalation;

import com.coreeng.supportbot.config.SlackTicketsProps;
import com.coreeng.supportbot.slack.MessageRef;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.coreeng.supportbot.slack.client.SlackGetMessageByTsRequest;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class EscalationValidator {
    private final SlackTicketsProps slackTicketsProps;
    private final SlackClient slackClient;
    private final EscalationQueryService queryService;

    @Nullable public String validateThreadPermalinkForCreation(@Nullable String threadPermalink) {
        if (threadPermalink == null) {
            return null;
        }

        MessageRef threadRef = MessageRef.fromPermalink(threadPermalink);
        if (!Objects.equals(slackTicketsProps.channelId(), threadRef.channelId())) {
            return "Link leads to the wrong channel. Channel for escalations is expected.";
        }
        if (threadRef.isReply()) {
            return "Thread permalink leads to a reply and not top-level message of the thread";
        }
        try {
            // verify that the passed link is a link to an actual message
            slackClient.getMessageByTs(SlackGetMessageByTsRequest.of(threadRef));
        } catch (Exception e) {
            log.atWarn()
                    .setCause(e)
                    .addArgument(threadPermalink)
                    .log("Couldn't fetch message by the link provided by user: {}");
            return "Couldn't find a message by the permalink";
        }
        if (queryService.existsByThreadTs(threadRef.ts())) {
            return "This thread is already used for an escalation";
        }
        return null;
    }
}
