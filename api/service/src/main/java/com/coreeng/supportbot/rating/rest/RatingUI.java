package com.coreeng.supportbot.rating.rest;

public record RatingUI(
        String name,
        Integer rating,
        String[] tags
) {
}