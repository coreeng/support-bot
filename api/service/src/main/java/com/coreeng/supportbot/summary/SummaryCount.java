package com.coreeng.supportbot.summary;

/**
 * One row of a ranked breakdown: a dimension value and how many of the window's tickets carry it.
 *
 * @param label the dimension value, already bucketed — blanks are replaced with an explicit label
 *     rather than dropped, so a breakdown always reconciles against the window total
 * @param count number of tickets
 */
public record SummaryCount(String label, long count) {}
