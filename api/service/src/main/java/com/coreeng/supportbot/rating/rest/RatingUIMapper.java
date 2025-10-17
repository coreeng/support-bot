package com.coreeng.supportbot.rating.rest;

import com.coreeng.supportbot.rating.Rating;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RatingUIMapper {
    public RatingUI mapToUI(Rating rating) {
        return new RatingUI(rating.impact(), rating.rating(), rating.tags());
    }
}
