package com.coreeng.supportbot.erasure.rest;

import com.coreeng.supportbot.erasure.ErasureRequestService;
import com.coreeng.supportbot.erasure.SubmitErasureRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Elevate's erasure orchestrator submits and polls Erasure Requests here, on the internal port only (ADR-010). */
@RestController
@ConditionalOnProperty(name = "internal-port.enabled", havingValue = "true")
@RequestMapping("/api/erasure-requests")
@PreAuthorize("hasAuthority('elevate:principal:service')")
@RequiredArgsConstructor
public class ErasureRequestController {

    private final ErasureRequestService erasureRequestService;
    private final ErasureRequestMapper mapper;

    @PostMapping
    public ResponseEntity<ErasureRequestResponse> submit(@Valid @RequestBody SubmitErasureRequest request) {
        return ResponseEntity.accepted().body(mapper.mapToResponse(erasureRequestService.submit(request)));
    }

    @GetMapping("/{requestId}")
    public ResponseEntity<ErasureRequestResponse> get(@PathVariable UUID requestId) {
        return ResponseEntity.ok(mapper.mapToResponse(erasureRequestService.get(requestId)));
    }
}
