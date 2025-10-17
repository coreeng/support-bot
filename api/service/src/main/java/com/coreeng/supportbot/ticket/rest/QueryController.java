package com.coreeng.supportbot.ticket.rest;

import com.coreeng.supportbot.slack.MessageRef;
import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.coreeng.supportbot.slack.client.SlackGetMessageByTsRequest;
import com.coreeng.supportbot.ticket.Ticket;
import com.coreeng.supportbot.ticket.TicketId;
import com.coreeng.supportbot.ticket.TicketQueryService;
import com.slack.api.model.Message;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/query")
@RequiredArgsConstructor
public class QueryController {
    private final TicketQueryService queryService;
    private final SlackClient slackClient;

    @GetMapping
    ResponseEntity<QueryUI> getQueryMessage(@RequestParam("ticketId") String ticketId) {
        long numericTicketId = Long.parseLong(ticketId);

        Ticket byId = queryService.findById(new TicketId(numericTicketId));
        if (byId != null && byId.createdMessageTs() != null) {
            MessageRef messageRef = new MessageRef(MessageTs.of(byId.createdMessageTs().ts()), byId.channelId());
            Message messageByTs = slackClient.getMessageByTs(SlackGetMessageByTsRequest.of(messageRef));
            return ResponseEntity.ok(QueryUI.builder().message(messageByTs.getText()).build());
        }
        return ResponseEntity.notFound().build();
    }
}
