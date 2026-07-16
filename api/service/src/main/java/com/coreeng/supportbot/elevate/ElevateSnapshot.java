package com.coreeng.supportbot.elevate;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ElevateSnapshot(
        List<ElevateProduct> products,
        List<ElevateUser> users,
        List<ElevateJourney> journeys,
        Map<String, JsonNode> productPayloads,
        Map<UUID, JsonNode> userPayloads,
        Map<String, JsonNode> journeyPayloads) {

    public ElevateSnapshot(List<ElevateProduct> products, List<ElevateUser> users, List<ElevateJourney> journeys) {
        this(products, users, journeys, Map.of(), Map.of(), Map.of());
    }

    public ElevateSnapshot {
        products = List.copyOf(products);
        users = List.copyOf(users);
        journeys = List.copyOf(journeys);
        productPayloads = Map.copyOf(productPayloads);
        userPayloads = Map.copyOf(userPayloads);
        journeyPayloads = Map.copyOf(journeyPayloads);
    }
}
