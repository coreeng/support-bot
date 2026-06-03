package com.coreeng.supportbot.prtracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.coreeng.supportbot.config.PrTrackingProps;
import com.coreeng.supportbot.prtracking.source.Provider;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PrMessageRendererTest {

    private static final String REPO = "my-org/my-repo";

    @Mock
    private PrTrackingProps prTrackingProps;

    private PrMessageRenderer rendererWith(PrTrackingProps.Messages messages) {
        when(prTrackingProps.repositories())
                .thenReturn(List.of(new PrTrackingProps.Repository(
                        REPO,
                        "wow",
                        null,
                        List.of(),
                        new PrTrackingProps.Sla(null, Duration.ofDays(1), null),
                        messages)));
        return new PrMessageRenderer(prTrackingProps, new PrUrlResolver(prTrackingProps));
    }

    private PrMessageContext ctx() {
        return new PrMessageContext(
                Provider.GITHUB, REPO, 42, "wow", Duration.ofHours(24), Instant.parse("2024-06-01T14:00:00Z"));
    }

    @Test
    void returnsNullWhenNoOverrideConfiguredForRepo() {
        when(prTrackingProps.repositories()).thenReturn(List.of());
        PrMessageRenderer renderer = new PrMessageRenderer(prTrackingProps, new PrUrlResolver(prTrackingProps));

        assertThat(renderer.render(REPO, MessageEvent.DETECTED, ctx())).isNull();
        assertThat(renderer.hasOverride(REPO, MessageEvent.DETECTED)).isFalse();
    }

    @Test
    void returnsNullWhenEventNotConfiguredForRepo() {
        PrMessageRenderer renderer =
                rendererWith(new PrTrackingProps.Messages(null, null, "\"PR approved!\"", null, null, null));

        assertThat(renderer.render(REPO, MessageEvent.DETECTED, ctx())).isNull();
        assertThat(renderer.hasOverride(REPO, MessageEvent.DETECTED)).isFalse();
        assertThat(renderer.hasOverride(REPO, MessageEvent.APPROVED)).isTrue();
    }

    @Test
    void rendersLiteralStringExpression() {
        PrMessageRenderer renderer = rendererWith(
                new PrTrackingProps.Messages("\"Contact support for help.\"", null, null, null, null, null));

        assertThat(renderer.render(REPO, MessageEvent.DETECTED, ctx())).isEqualTo("Contact support for help.");
    }

    @Test
    void rendersExpressionWithPrNumberVariable() {
        PrMessageRenderer renderer = rendererWith(new PrTrackingProps.Messages(
                "\"PR \" + string(pr_number) + \" detected.\"", null, null, null, null, null));

        assertThat(renderer.render(REPO, MessageEvent.DETECTED, ctx())).isEqualTo("PR 42 detected.");
    }

    @Test
    void rendersExpressionWithRepoNameVariable() {
        PrMessageRenderer renderer =
                rendererWith(new PrTrackingProps.Messages(null, null, null, null, "\"Merged in \" + repo_name", null));

        assertThat(renderer.render(REPO, MessageEvent.MERGED, ctx())).isEqualTo("Merged in my-org/my-repo");
    }

    @Test
    void rendersExpressionWithSlaDurationVariable() {
        PrMessageRenderer renderer =
                rendererWith(new PrTrackingProps.Messages(null, "\"SLA was \" + sla_duration", null, null, null, null));

        assertThat(renderer.render(REPO, MessageEvent.ESCALATED, ctx())).isEqualTo("SLA was 1 day");
    }

    @Test
    void rendersExpressionWithSlaDeadlineVariable() {
        PrMessageRenderer renderer = rendererWith(
                new PrTrackingProps.Messages("\"Deadline: \" + sla_deadline", null, null, null, null, null));

        assertThat(renderer.render(REPO, MessageEvent.DETECTED, ctx())).isEqualTo("Deadline: Sat 01 Jun at 14:00 UTC");
    }

    @Test
    void rendersExpressionWithOwningTeamVariable() {
        PrMessageRenderer renderer = rendererWith(
                new PrTrackingProps.Messages(null, null, null, "\"Changes for \" + owning_team", null, null));

        assertThat(renderer.render(REPO, MessageEvent.CHANGES_REQUESTED, ctx())).isEqualTo("Changes for wow");
    }

    @Test
    void rendersEmptySlaDurationWhenNoSla() {
        PrMessageContext noSlaCtx = new PrMessageContext(Provider.GITHUB, REPO, 42, "wow", null, null);
        PrMessageRenderer renderer = rendererWith(
                new PrTrackingProps.Messages("\"duration:\" + sla_duration", null, null, null, null, null));

        assertThat(renderer.render(REPO, MessageEvent.DETECTED, noSlaCtx)).isEqualTo("duration:");
    }

    @Test
    void returnsNullWhenCelExpressionEvaluatesToNonString() {
        // CEL integer literal — valid expression, but returns a non-String value.
        PrMessageRenderer renderer = rendererWith(new PrTrackingProps.Messages(null, null, "42", null, null, null));

        assertThat(renderer.render(REPO, MessageEvent.APPROVED, ctx())).isNull();
    }

    @Test
    void returnsNullForInvalidCelExpressionInsteadOfCrashingAtStartup() {
        // Invalid CEL logs an error but does not throw, so a misconfigured message cannot take the service down.
        PrMessageRenderer renderer =
                rendererWith(new PrTrackingProps.Messages("this is not valid CEL !!!", null, null, null, null, null));

        assertThat(renderer.render(REPO, MessageEvent.DETECTED, ctx())).isNull();
        assertThat(renderer.hasOverride(REPO, MessageEvent.DETECTED)).isFalse();
    }

    @Test
    void repoLookupIsCaseInsensitive() {
        PrMessageRenderer renderer =
                rendererWith(new PrTrackingProps.Messages("\"hello\"", null, null, null, null, null));

        assertThat(renderer.render("MY-ORG/MY-REPO", MessageEvent.DETECTED, ctx()))
                .isEqualTo("hello");
        assertThat(renderer.hasOverride("MY-ORG/MY-REPO", MessageEvent.DETECTED))
                .isTrue();
    }
}
