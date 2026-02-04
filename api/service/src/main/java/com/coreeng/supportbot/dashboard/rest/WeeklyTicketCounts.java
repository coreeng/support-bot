package com.coreeng.supportbot.dashboard.rest;

public record WeeklyTicketCounts(String week, long opened, long closed, long escalated, long stale) {}
