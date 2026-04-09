package com.coreeng.supportbot.ticket;

import com.coreeng.supportbot.enums.EscalationTeamsRegistry;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TicketEscalationValidator {
    private final EscalationTeamsRegistry escalationTeamsRegistry;

    public ValidationResult validate(@Nullable String team, @Nullable List<String> tags) {
        if (team == null || team.isBlank()) {
            return ValidationResult.invalid(Field.team, "team is required");
        }
        if (escalationTeamsRegistry.findEscalationTeamByCode(team) == null) {
            return ValidationResult.invalid(Field.team, "team must be a valid escalation team");
        }
        if (tags != null && tags.stream().anyMatch(Objects::isNull)) {
            return ValidationResult.invalid(Field.tags, "tags must not contain null");
        }
        return ValidationResult.valid();
    }

    public ValidationResult validate(EscalateRequest request) {
        return validate(request.team(), request.tags());
    }

    public enum Field {
        team,
        tags,
    }

    public record ValidationResult(
            boolean isValid,
            @Nullable Field field,
            @Nullable String errorMessage) {
        public static ValidationResult valid() {
            return new ValidationResult(true, null, null);
        }

        public static ValidationResult invalid(Field field, String errorMessage) {
            return new ValidationResult(false, field, errorMessage);
        }
    }
}
