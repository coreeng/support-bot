package com.coreeng.supportbot.rating.rest;

import com.coreeng.supportbot.rating.RatingService;
import com.google.common.collect.ImmutableList;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rating")
@RequiredArgsConstructor
public class RatingController {
    private final RatingService ratingService;
    private final RatingUIMapper mapper;

    @GetMapping
    public ResponseEntity<ImmutableList<RatingUI>> list() {
        ImmutableList<RatingUI> ratings = ratingService.getAllRatings()
                .stream().map(mapper::mapToUI).collect(ImmutableList.toImmutableList());
        return ResponseEntity.ok(ratings);
    }
}
