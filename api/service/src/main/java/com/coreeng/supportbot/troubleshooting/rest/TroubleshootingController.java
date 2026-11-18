package com.coreeng.supportbot.troubleshooting.rest;

import com.coreeng.supportbot.troubleshooting.ConditionalOnTroubleshootingEnabled;
import com.coreeng.supportbot.troubleshooting.TroubleshootingService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/troubleshooting/ticket/{ticketId}/session")
@ConditionalOnTroubleshootingEnabled
@RequiredArgsConstructor
public class TroubleshootingController {
    private final TroubleshootingService service;

    @GetMapping
    public TroubleshootingSessionUI get(@PathVariable long ticketId) {
        return TroubleshootingMapper.toUI(service.get(ticketId));
    }

    @PostMapping
    public TroubleshootingSessionUI start(@PathVariable long ticketId) {
        return TroubleshootingMapper.toUI(service.start(ticketId));
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
