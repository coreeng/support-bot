package com.coreeng.supportbot.testkit;

import org.jspecify.annotations.NonNull;

import com.google.common.collect.ImmutableList;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class FullSummaryFormSubmission implements ViewSubmission {
    @NonNull
    private final String triggerId;
    @NonNull
    private final String callbackId;
    private final long ticketId;
    @NonNull
    private final Values values;

    @Builder
    @Getter
    public static class Values {
        private final Ticket.@NonNull Status status;
        @NonNull
        private final String team;
        @NonNull
        private final ImmutableList<@NonNull String> tags;
        @NonNull
        private final String impact;
    }

    @Override
    public String viewType() {
        return "modal";
    }

    @Override
    public ImmutableList<@NonNull Value> values() {
        return ImmutableList.of(
            new StaticSelectValue("change-status", values.status().code()),
            new StaticSelectValue("change-team", values.team()),
            new MultiStaticSelectValue("change-tags", values.tags()),
            new StaticSelectValue("change-impact", values.impact())
        );
    }

    @Override
    public String privateMetadata() {
        return String.format("""
                {"ticketId": %d}
                """, ticketId);
    }
}
