package com.coreeng.supportbot.dashboard;

import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record IncomingVsResolvedQuery(
        @Nullable LocalDate dateFrom,
        @Nullable LocalDate dateTo,
        boolean allTime,
        List<String> teams,
        @Nullable Granularity granularity) {

    public IncomingVsResolvedQuery(
            @Nullable LocalDate dateFrom,
            @Nullable LocalDate dateTo,
            boolean allTime,
            @Nullable List<String> teams,
            @Nullable Granularity granularity) {
        this.dateFrom = dateFrom;
        this.dateTo = dateTo;
        this.allTime = allTime;
        this.teams = teams == null ? List.of() : List.copyOf(teams);
        this.granularity = granularity;
    }

    public enum Granularity {
        AUTO,
        HOUR,
        DAY,
        WEEK
    }
}
