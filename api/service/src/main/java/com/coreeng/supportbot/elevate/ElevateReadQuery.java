package com.coreeng.supportbot.elevate;

import org.jspecify.annotations.Nullable;

public record ElevateReadQuery(
        int page,
        int pageSize,
        String query,
        @Nullable String exactId,
        ElevateRelationshipFilter relationship,
        ElevateSort sort,
        ElevateDirection direction) {

    public ElevateReadQuery {
        query = query.trim();
        exactId = exactId == null || exactId.isBlank() ? null : exactId.trim();
    }
}
