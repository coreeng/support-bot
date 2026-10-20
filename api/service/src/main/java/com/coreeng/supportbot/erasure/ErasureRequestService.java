package com.coreeng.supportbot.erasure;

import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Accepts and reports Erasure Requests (ADR-009). Nothing processes them yet, so every request stays in
 * progress. Log lines carry the request id only, never the emails.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErasureRequestService {

    private final ErasureRequestRepository repository;
    private final Clock clock;

    @Transactional
    public ErasureRequest submit(UUID requestId, String currentUserEmail, String newUserEmail) {
        boolean stored = repository.insertIfAbsent(new ErasureRequest(
                requestId, currentUserEmail, newUserEmail, ErasureRequestStatus.IN_PROGRESS, clock.instant()));
        ErasureRequest request = repository
                .findById(requestId)
                .orElseThrow(() -> new IllegalStateException("Erasure request " + requestId + " was not stored"));
        if (stored) {
            log.info("Erasure request {} accepted", requestId);
        } else {
            log.info(
                    "Erasure request {} already exists with status {}; submission ignored",
                    requestId,
                    request.status());
        }
        return request;
    }

    @Transactional(readOnly = true)
    public ErasureRequest get(UUID requestId) {
        return repository.findById(requestId).orElseThrow(() -> new ErasureRequestNotFoundException(requestId));
    }
}
