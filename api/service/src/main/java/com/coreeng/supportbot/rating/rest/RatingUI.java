package com.coreeng.supportbot.rating.rest;

import java.util.List;

public record RatingUI(
        String name,
        Integer rating,
        List<String> tags
) {
}