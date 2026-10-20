package com.coreeng.supportbot.erasure;

import java.util.UUID;

public class ErasureRequestNotFoundException extends RuntimeException {

    public ErasureRequestNotFoundException(UUID requestId) {
        super("Erasure request " + requestId + " not found");
    }
}
