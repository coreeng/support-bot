package com.coreeng.supportbot.ticket.rest;

import com.coreeng.supportbot.ticket.TicketStatus;
import com.google.common.collect.ImmutableList;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile({"functionaltests", "nft"})
@RequestMapping("/test/ticket")
@RequiredArgsConstructor
public class TicketTestController {
    private final TicketTestService ticketTestService;

    @PostMapping
    public TicketUI createTicket(@RequestBody TicketToCreate ticketToCreate) {
        return ticketTestService.createTicket(ticketToCreate);
    }

    @GetMapping("/by-query")
    public ResponseEntity<TicketUI> findTicketByQuery(
            @RequestParam("channelId") String channelId, @RequestParam("messageTs") String messageTs) {
        TicketUI ticket = ticketTestService.findTicketByQuery(channelId, messageTs);
        if (ticket == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ticket);
    }

    @PatchMapping("/update")
    public ResponseEntity<?> updateTicket(@RequestBody TicketToUpdate request) {
        TicketTestService.UpdateResult result = ticketTestService.updateTicket(request);
        if (result.ticket() == null) {
            return ResponseEntity.internalServerError().body(result.error());
        }
        return ResponseEntity.ok(result.ticket());
    }

    public record TicketToCreate(String queryTs, String createdMessageTs, String channelId) {}

    public record TicketToUpdate(
            long ticketId, TicketStatus status, String authorsTeam, ImmutableList<String> tags, String impact) {}
}
