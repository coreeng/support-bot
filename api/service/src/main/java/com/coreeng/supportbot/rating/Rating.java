package com.coreeng.supportbot.rating;

import com.coreeng.supportbot.ticket.TicketStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.With;

import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.UUID;

@Getter
@Builder(toBuilder = true)
public class Rating {
    @With
    @Nullable
    private UUID id;
    private Integer rating;
    private String submittedTs;
    private TicketStatus status;
    @Nullable
    private String impact;
    @Nullable
    private List<String> tags;
    private boolean isEscalated;
}
