package com.coreeng.supportbot.elevate;

import java.util.List;

public record ElevateSnapshot(List<ElevateProduct> products, List<ElevateUser> users, List<ElevateJourney> journeys) {

    public ElevateSnapshot {
        products = List.copyOf(products);
        users = List.copyOf(users);
        journeys = List.copyOf(journeys);
    }
}
