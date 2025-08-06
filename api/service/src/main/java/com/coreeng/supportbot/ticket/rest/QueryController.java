package com.coreeng.supportbot.ticket.rest;

import com.coreeng.supportbot.slack.MessageRef;
import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.ticket.TicketQueryService;
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

    @GetMapping
    ResponseEntity<Void> checkQueryExists(@RequestParam("channelId") String channelId,
                                          @RequestParam("messageTs") String messageTs) {
        MessageRef messageRef = new MessageRef(MessageTs.of(messageTs), channelId);
        if (queryService.queryExists(messageRef)) {
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}
