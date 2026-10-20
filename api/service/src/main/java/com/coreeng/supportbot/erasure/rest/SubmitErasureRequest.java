package com.coreeng.supportbot.erasure.rest;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Elevate sends every participant one command, so a field it adds must not become a 400 retried forever. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SubmitErasureRequest(
        @NotNull UUID requestId,
        @NotBlank @Email @Size(max = 255) String currentUserEmail,
        @NotBlank @Email @Size(max = 255) String newUserEmail) {

    /** Leaves out the emails so a logged request cannot disclose them. */
    @Override
    public String toString() {
        return "SubmitErasureRequest[requestId=" + requestId + "]";
    }
}
