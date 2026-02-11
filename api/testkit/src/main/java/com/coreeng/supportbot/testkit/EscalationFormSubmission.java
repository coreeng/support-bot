package com.coreeng.supportbot.testkit;

import com.google.common.collect.ImmutableList;
import lombok.Builder;
import lombok.Getter;
import org.jspecify.annotations.NonNull;

@Builder
@Getter
public class EscalationFormSubmission implements ViewSubmission {
    @NonNull private final String triggerId;

    private final long ticketId;

    @NonNull private final Values values;

    @Builder
    @Getter
    public static class Values {
        @NonNull private final String team;

        @NonNull private final ImmutableList<String> tags;
    }

    @Override
    public String callbackId() {
        return "ticket-escalate";
    }

    @Override
    public String viewType() {
        return "modal";
    }

    @Override
    public String privateMetadata() {
        return String.format("""
                {"ticketId": %d}
                """, ticketId);
    }

    @Override
    public ImmutableList<Value> values() {
        return ImmutableList.of(
                new StaticSelectValue("escalation-team", values.team()),
                new MultiStaticSelectValue("escalation-tags", values.tags()));
    }
}
