package com.coreeng.supportbot.ticket;

import static org.assertj.core.api.Assertions.assertThat;

import com.coreeng.supportbot.enums.EscalationTeam;
import com.coreeng.supportbot.teams.fakes.FakeEscalationTeamsRegistry;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TicketEscalationValidatorTest {
    private TicketEscalationValidator validator;

    @BeforeEach
    void setUp() {
        validator = new TicketEscalationValidator(new FakeEscalationTeamsRegistry(
                List.of(new EscalationTeam("Core Support", "core-support", "slack:S123"))));
    }

    @Test
    void validate_returnsErrorWhenTeamIsMissing() {
        TicketEscalationValidator.ValidationResult result = validator.validate((String) null, List.of("bug"));

        assertThat(result.isValid()).isFalse();
        assertThat(result.field()).isEqualTo(TicketEscalationValidator.Field.team);
        assertThat(result.errorMessage()).isEqualTo("team is required");
    }

    @Test
    void validate_returnsErrorWhenTeamIsUnknown() {
        TicketEscalationValidator.ValidationResult result = validator.validate("unknown-team", List.of("bug"));

        assertThat(result.isValid()).isFalse();
        assertThat(result.field()).isEqualTo(TicketEscalationValidator.Field.team);
        assertThat(result.errorMessage()).isEqualTo("team must be a valid escalation team");
    }

    @Test
    void validate_returnsErrorWhenTagsContainNull() {
        TicketEscalationValidator.ValidationResult result =
                validator.validate("core-support", Arrays.asList("bug", null));

        assertThat(result.isValid()).isFalse();
        assertThat(result.field()).isEqualTo(TicketEscalationValidator.Field.tags);
        assertThat(result.errorMessage()).isEqualTo("tags must not contain null");
    }

    @Test
    void validate_returnsValidForKnownTeamAndTags() {
        TicketEscalationValidator.ValidationResult result = validator.validate("core-support", List.of("bug"));

        assertThat(result.isValid()).isTrue();
        assertThat(result.field()).isNull();
        assertThat(result.errorMessage()).isNull();
    }
}
