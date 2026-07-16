package com.coreeng.supportbot.elevate;

public record ElevateIntegrityCounts(
        long orphanJourneys, long orphanUsers, long missingAssignments, long crossProductAssignments) {}
