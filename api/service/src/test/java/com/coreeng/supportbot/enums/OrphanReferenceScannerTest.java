package com.coreeng.supportbot.enums;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.when;

import com.coreeng.supportbot.config.EnumProps;
import com.google.common.collect.ImmutableList;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

@ExtendWith(MockitoExtension.class)
class OrphanReferenceScannerTest {

    @Mock
    private OrphanReferenceRepository repository;

    @Mock
    private EnumProps enumProps;

    @Test
    void run_registersGaugesFromRepositoryCounts() {
        when(enumProps.escalationTeams()).thenReturn(ImmutableList.of());
        when(repository.countRetiredImpactReferences()).thenReturn(2L);
        when(repository.countRetiredTagReferences()).thenReturn(3L);
        when(repository.countOrphanedEscalationTeamReferences(ImmutableList.of()))
                .thenReturn(4L);
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        OrphanReferenceScanner scanner = new OrphanReferenceScanner(repository, enumProps, meterRegistry);

        scanner.run(new DefaultApplicationArguments());

        assertThat(gauge(meterRegistry, "impact")).isEqualTo(2.0);
        assertThat(gauge(meterRegistry, "tag")).isEqualTo(3.0);
        assertThat(gauge(meterRegistry, "escalation_team")).isEqualTo(4.0);
    }

    @Test
    void run_isNonFatalWhenRepositoryThrows() {
        when(enumProps.escalationTeams()).thenReturn(ImmutableList.of());
        when(repository.countRetiredImpactReferences()).thenThrow(new RuntimeException("db unavailable"));
        OrphanReferenceScanner scanner = new OrphanReferenceScanner(repository, enumProps, new SimpleMeterRegistry());

        // The scan must never block startup.
        assertThatCode(() -> scanner.run(new DefaultApplicationArguments())).doesNotThrowAnyException();
    }

    private static double gauge(MeterRegistry registry, String type) {
        return registry.get("support_bot.orphaned_references")
                .tag("type", type)
                .gauge()
                .value();
    }
}
