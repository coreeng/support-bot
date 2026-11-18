package com.coreeng.supportbot.troubleshooting;

import com.coreeng.supportbot.troubleshooting.router.RouterAction;
import org.jspecify.annotations.Nullable;

/**
 * Shapes of the JSON stored in {@code troubleshooting_event.payload}, one per event type. Who an
 * event is from lives in {@code troubleshooting_event.actor} only, never in a payload, so erasing a
 * person has one column to rewrite plus the free-text fields ({@code text}, {@code reason},
 * {@code error}); see {@code docs/runbooks/troubleshooting-sessions.md}.
 */
public final class Payloads {
    private Payloads() {}

    public record SlackMessage(@Nullable String ts, String text, boolean available) {}

    public record EngineerMessage(String text) {}

    public record RouterMessage(String text, String model, long inputTokens, long outputTokens, long durationMs) {}

    public record Proposal(
            RouterAction action,
            String text,
            String reason,
            String model,
            long inputTokens,
            long outputTokens,
            long durationMs) {}

    public record Decision(
            long proposalSeq,
            DecisionKind decision,
            @Nullable String text) {}

    public record RunUpdate(String kind, String text) {}

    public record RouterStatus(RouterState state, @Nullable String error) {}

    public enum DecisionKind {
        APPROVED,
        REJECTED,
        SUPERSEDED
    }

    public enum RouterState {
        THINKING,
        IDLE,
        FAILED
    }
}
