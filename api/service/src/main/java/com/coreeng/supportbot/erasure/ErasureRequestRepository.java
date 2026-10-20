package com.coreeng.supportbot.erasure;

import java.util.Optional;
import java.util.UUID;

public interface ErasureRequestRepository {

    /**
     * Stores the request unless one with the same id already exists, which is left unchanged.
     *
     * @return true if the request was stored
     */
    boolean insertIfAbsent(ErasureRequest request);

    Optional<ErasureRequest> findById(UUID id);
}
