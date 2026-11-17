package com.coreeng.supportbot.troubleshooting.rest;

import com.coreeng.supportbot.security.UserPrincipal;
import com.coreeng.supportbot.troubleshooting.ConditionalOnTroubleshootingEnabled;
import com.coreeng.supportbot.troubleshooting.Payloads.DecisionKind;
import com.coreeng.supportbot.troubleshooting.TroubleshootingService;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/troubleshooting/ticket/{ticketId}/session")
@ConditionalOnTroubleshootingEnabled
@RequiredArgsConstructor
public class TroubleshootingController {
    private static final int MAX_MESSAGE_CHARS = 8_000;

    private final TroubleshootingService service;

    @GetMapping
    public TroubleshootingSessionUI get(@PathVariable long ticketId) {
        return TroubleshootingMapper.toUI(service.get(ticketId));
    }

    @PostMapping
    public TroubleshootingSessionUI start(@PathVariable long ticketId) {
        return TroubleshootingMapper.toUI(service.start(ticketId));
    }

    @PostMapping("/messages")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TroubleshootingEventUI send(
            @PathVariable long ticketId,
            @RequestBody MessageRequest request,
            @AuthenticationPrincipal UserPrincipal user) {
        String text = request.text() == null ? "" : request.text().trim();
        if (text.isEmpty() || text.length() > MAX_MESSAGE_CHARS) {
            throw new InvalidTroubleshootingRequestException(
                    "Message text must be between 1 and " + MAX_MESSAGE_CHARS + " characters");
        }
        return TroubleshootingMapper.toUI(service.sendMessage(ticketId, user.email(), text));
    }

    @PostMapping("/proposals/{seq}/decision")
    public TroubleshootingEventUI decide(
            @PathVariable long ticketId,
            @PathVariable long seq,
            @RequestBody DecisionRequest request,
            @AuthenticationPrincipal UserPrincipal user) {
        String decision = request.decision() == null ? "" : request.decision().toUpperCase(Locale.ROOT);
        DecisionKind kind =
                switch (decision) {
                    case "APPROVED" -> DecisionKind.APPROVED;
                    case "REJECTED" -> DecisionKind.REJECTED;
                    default ->
                        throw new InvalidTroubleshootingRequestException("Decision must be APPROVED or REJECTED");
                };
        return TroubleshootingMapper.toUI(service.decide(ticketId, seq, user.email(), kind, request.text()));
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @PathVariable long ticketId,
            @RequestParam(defaultValue = "0") long after,
            @RequestHeader(name = "Last-Event-ID", required = false) @Nullable Long lastEventId) {
        long from = Math.max(after, lastEventId == null ? 0 : lastEventId);
        return service.stream(ticketId, from, TroubleshootingMapper::toUI);
    }
}
