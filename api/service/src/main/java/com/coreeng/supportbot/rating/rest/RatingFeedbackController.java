package com.coreeng.supportbot.rating.rest;

import com.coreeng.supportbot.rating.RatingFeedbackItem;
import com.coreeng.supportbot.rating.RatingService;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Free-text rating feedback. Restricted to leadership and support engineers in {@code SecurityConfig}. */
@RestController
@RequestMapping("/rating")
@RequiredArgsConstructor
public class RatingFeedbackController {
    private final RatingService ratingService;

    @GetMapping("/feedback")
    public List<RatingFeedbackItem> feedback(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate dateTo) {
        return ratingService.getFeedback(dateFrom, dateTo);
    }
}
