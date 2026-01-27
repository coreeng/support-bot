package com.coreeng.supportbot.testkit;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

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
        @Nullable
        private final String assignedTo;  // Slack user ID, optional
    }

    @Override
    public String viewType() {
        return "modal";
    }

    @Override
    public ImmutableList<@NonNull Value> values() {
        ImmutableList.Builder<Value> builder = ImmutableList.<Value>builder()
            .add(new StaticSelectValue("ticket-change-status", values.status().code()))
            .add(new StaticSelectValue("ticket-change-team", values.team()))
            .add(new MultiStaticSelectValue("ticket-change-tags", values.tags()))
            .add(new StaticSelectValue("ticket-change-impact", values.impact()));
        
        if (values.assignedTo() != null) {
            builder.add(new StaticSelectValue("ticket-change-assignee", values.assignedTo()));
        }
        
        return builder.build();
    }

    @Override
    public String privateMetadata() {
        return String.format("""
                {"ticketId": %d}
                """, ticketId);
    }
}
