package com.coreeng.supportbot.elevate;

public record ElevateReadQuery(
        int page,
        int pageSize,
        String query,
        ElevateRelationshipFilter relationship,
        ElevateSort sort,
        ElevateDirection direction) {}
