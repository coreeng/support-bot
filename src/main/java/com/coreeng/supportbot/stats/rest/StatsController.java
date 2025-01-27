package com.coreeng.supportbot.stats.rest;

import com.coreeng.supportbot.stats.StatsRequest;
import com.coreeng.supportbot.stats.StatsResult;
import com.coreeng.supportbot.stats.StatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.google.common.collect.ImmutableList.toImmutableList;

@Slf4j
@RestController
@RequestMapping("/stats")
@RequiredArgsConstructor
public class StatsController {
    private final StatsService statsService;

    @GetMapping
    public List<StatsResult> stats(@RequestBody List<StatsRequest> request) {
        return request.stream()
            .map(statsService::calculate)
            .collect(toImmutableList());
    }
}
