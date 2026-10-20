package com.coreeng.supportbot.erasure.rest;

import com.coreeng.supportbot.erasure.ErasureRequest;
import org.springframework.stereotype.Component;

@Component
public class ErasureRequestMapper {

    public ErasureRequestResponse mapToResponse(ErasureRequest request) {
        return new ErasureRequestResponse(request.id(), request.status(), request.requestedAt());
    }
}
