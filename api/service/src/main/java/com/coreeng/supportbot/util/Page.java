package com.coreeng.supportbot.util;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import java.util.function.Function;

public record Page<T>(ImmutableList<T> content, long page, long totalPages, long totalElements) {
    public <Y> Page<Y> map(Function<T, Y> mapperFn) {
        return new Page<>(content.stream().map(mapperFn).collect(toImmutableList()), page, totalPages, totalElements);
    }
}
