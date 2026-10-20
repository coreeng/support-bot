package com.coreeng.supportbot.erasure.rest;

import com.coreeng.supportbot.erasure.ErasureRequest;
import com.coreeng.supportbot.erasure.ErasureRequestService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/api/erasure-requests")
@PreAuthorize("hasAuthority('elevate:principal:service')")
@RequiredArgsConstructor
public class ErasureRequestController {

    private final ErasureRequestService erasureRequestService;

    @PostMapping
    public ResponseEntity<ErasureRequestResponse> submit(@Valid @RequestBody SubmitErasureRequest request) {
        ErasureRequest stored =
                erasureRequestService.submit(request.requestId(), request.currentUserEmail(), request.newUserEmail());
        return ResponseEntity.accepted()
                .location(URI.create("/api/erasure-requests/" + stored.id()))
                .body(ErasureRequestResponse.from(stored));
    }

    @GetMapping("/{requestId}")
    public ResponseEntity<ErasureRequestResponse> get(@PathVariable UUID requestId) {
        return ResponseEntity.ok(ErasureRequestResponse.from(erasureRequestService.get(requestId)));
    }
}
