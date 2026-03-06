package com.coreeng.supportbot.prtracking;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.coreeng.supportbot.config.PrTrackingProps;
import com.coreeng.supportbot.enums.ImpactsRegistry;
import com.coreeng.supportbot.enums.Tag;
import com.coreeng.supportbot.enums.TagsRegistry;
import com.coreeng.supportbot.enums.TicketImpact;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;

/**
 * Unit tests for {@link PrTrackingStartupValidator}.
 *
 * <p>The validator is gated by {@code @ConditionalOnProperty(name = "pr-review-tracking.enabled",
 * havingValue = "true")}, so the Spring bean — and therefore this validation — is never created
 * when the feature is disabled.
 */
@ExtendWith(MockitoExtension.class)
class PrTrackingStartupValidatorTest {

    @Mock
    private PrTrackingProps props;

    @Mock
    private TagsRegistry tagsRegistry;

    @Mock
    private ImpactsRegistry impactsRegistry;

    private PrTrackingStartupValidator validator;

    @BeforeEach
    void setUp() {
        validator = new PrTrackingStartupValidator(props, tagsRegistry, impactsRegistry);
    }

    @Test
    void passesWhenAllTagsAndImpactAreKnown() {
        // given
        when(props.tags()).thenReturn(List.of("networking", "vault"));
        when(props.impact()).thenReturn("low");
        when(tagsRegistry.listTagsByCodes(ImmutableList.of("networking")))
                .thenReturn(ImmutableList.of(new Tag("Networking", "networking")));
        when(tagsRegistry.listTagsByCodes(ImmutableList.of("vault")))
                .thenReturn(ImmutableList.of(new Tag("Vault", "vault")));
        when(impactsRegistry.findImpactByCode("low")).thenReturn(new TicketImpact("Low", "low"));

        // when / then
        assertThatCode(() -> validator.run(mock(ApplicationArguments.class))).doesNotThrowAnyException();
    }

    @Test
    void throwsForUnknownTagCode() {
        // given
        when(props.tags()).thenReturn(List.of("unknown-tag"));
        when(tagsRegistry.listTagsByCodes(ImmutableList.of("unknown-tag"))).thenReturn(ImmutableList.of());

        // when / then
        assertThatThrownBy(() -> validator.run(mock(ApplicationArguments.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unknown-tag")
                .hasMessageContaining("enums.tags");
    }

    @Test
    void throwsOnFirstUnknownTagWhenMultipleTagsConfigured() {
        // given
        when(props.tags()).thenReturn(List.of("valid-tag", "unknown-tag"));
        when(tagsRegistry.listTagsByCodes(ImmutableList.of("valid-tag")))
                .thenReturn(ImmutableList.of(new Tag("Valid Tag", "valid-tag")));
        when(tagsRegistry.listTagsByCodes(ImmutableList.of("unknown-tag"))).thenReturn(ImmutableList.of());

        // when / then
        assertThatThrownBy(() -> validator.run(mock(ApplicationArguments.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unknown-tag");
    }

    @Test
    void throwsForUnknownImpactCode() {
        // given
        when(props.tags()).thenReturn(List.of("networking"));
        when(props.impact()).thenReturn("unknown-impact");
        when(tagsRegistry.listTagsByCodes(ImmutableList.of("networking")))
                .thenReturn(ImmutableList.of(new Tag("Networking", "networking")));
        when(impactsRegistry.findImpactByCode("unknown-impact")).thenReturn(null);

        // when / then
        assertThatThrownBy(() -> validator.run(mock(ApplicationArguments.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unknown-impact")
                .hasMessageContaining("enums.impacts");
    }
}
