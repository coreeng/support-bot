package com.coreeng.supportbot.rating;

import lombok.Builder;
import lombok.Getter;
import lombok.With;

import javax.annotation.Nullable;
import java.util.UUID;

@Getter
@Builder(toBuilder = true)
public class Rating {
    @With
    @Nullable
    private UUID id;
    private Integer rating;
    private String submittedTs;
    private String status;
    @Nullable
    private String impact;
    @Nullable
    private String[] tags;
    private boolean isEscalated;

    public boolean isValidRating() {
        return rating >= 1 && rating <= 5;
    }

    public boolean isHighRating() {
        return rating >= 4;
    }

    public boolean isLowRating() {
        return rating <= 2;
    }

    public boolean isEscalated() {
        return isEscalated;
    }
}
