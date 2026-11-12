package com.coreeng.supportbot.summary;

import com.google.common.collect.ImmutableList;

/**
 * One run of a ready summary's prose: plain text, or a claim with the tickets behind it.
 *
 * @param text the run's text, exactly as the reader sees it
 * @param evidence the cited tickets that still resolve in the summary's scope, in the model's order;
 *     empty for plain text
 */
public record SummarySegment(String text, ImmutableList<SummaryEvidence> evidence) {}
