package com.coreeng.supportbot.troubleshooting;

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
}
