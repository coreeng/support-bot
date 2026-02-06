package com.coreeng.supportbot.ticket.rest;

import com.coreeng.supportbot.enums.ImpactsRegistry;
import com.coreeng.supportbot.enums.TicketImpact;
import com.coreeng.supportbot.teams.PlatformTeam;
import com.coreeng.supportbot.teams.PlatformTeamsService;
import com.coreeng.supportbot.ticket.*;
import com.google.common.collect.ImmutableList;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import static java.util.Objects.requireNonNull;

@Service
@RequiredArgsConstructor
public class TicketUpdateService {
    private final TicketProcessingService ticketProcessingService;
    private final TicketQueryService queryService;
    private final ImpactsRegistry impactsRegistry;
    private final TicketUIMapper mapper;
    private final PlatformTeamsService platformTeamsService;

    public TicketUI update(TicketId ticketId, @Nullable TicketUpdateRequest request) {
        ValidationResult validationResult = validate(request);
        if (!validationResult.isValid()) {
            throw new IllegalArgumentException(validationResult.errorMessage());
        }
        TicketUpdateRequest validatedRequest = requireNonNull(request);

        TicketSubmission submission = buildSubmission(ticketId, validatedRequest);
        TicketSubmitResult result = ticketProcessingService.submit(submission);

        if (!(result instanceof TicketSubmitResult.Success)) {
            throw new IllegalStateException("Update failed: " + result);
        }

        DetailedTicket ticket = queryService.findDetailedById(ticketId);
        return mapper.mapToUI(requireNonNull(ticket));
    }

    private TicketSubmission buildSubmission(TicketId ticketId, TicketUpdateRequest request) {
        return TicketSubmission.builder()
            .ticketId(ticketId)
            .status(requireNonNull(request.status()))
            .authorsTeam(TicketTeam.fromCode(requireNonNull(request.authorsTeam())))
            .tags(ImmutableList.copyOf(requireNonNull(request.tags())))
            .impact(requireNonNull(request.impact()))
            .assignedTo(request.assignedTo())
            .confirmed(true)
            .build();
    }

    private ValidationResult validate(@Nullable TicketUpdateRequest request) {
        if (request == null) {
            return ValidationResult.invalid("Request body is required");
        }
        if (request.status() == null) {
            return ValidationResult.invalid("status is required and must be a valid TicketStatus");
        }
        if (request.authorsTeam() == null || request.authorsTeam().isBlank()) {
            return ValidationResult.invalid("authorsTeam is required");
        }
        boolean isNotATenant = TicketTeam.notATenantCode.equals(request.authorsTeam());
        PlatformTeam team = platformTeamsService.findTeamByName(request.authorsTeam());
        if (!isNotATenant && team == null) {
            return ValidationResult.invalid("authorsTeam must be a valid team code");
        }
        if (request.tags() == null) {
            return ValidationResult.invalid("tags is required");
        }
        if (request.tags().isEmpty()) {
            return ValidationResult.invalid("tags must contain at least one value");
        }
        if (request.impact() == null || request.impact().isBlank()) {
            return ValidationResult.invalid("impact is required");
        }
        TicketImpact impact = impactsRegistry.findImpactByCode(request.impact());
        if (impact == null) {
            return ValidationResult.invalid("impact must be a valid TicketImpact code");
        }
        return ValidationResult.valid();
    }

    record ValidationResult(boolean isValid, @Nullable String errorMessage) {
        static ValidationResult valid() {
            return new ValidationResult(true, null);
        }

        static ValidationResult invalid(String errorMessage) {
            return new ValidationResult(false, errorMessage);
        }
    }
}
