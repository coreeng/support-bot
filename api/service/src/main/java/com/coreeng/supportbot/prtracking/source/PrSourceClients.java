package com.coreeng.supportbot.prtracking.source;

import java.util.EnumMap;
import java.util.Map;

public class PrSourceClients {

    private final Map<Provider, PrSourceClient> clientsByProvider;

    public PrSourceClients(Map<Provider, PrSourceClient> clientsByProvider) {
        this.clientsByProvider = new EnumMap<>(clientsByProvider);
    }

    public PrSourceClient forProvider(Provider provider) {
        PrSourceClient client = clientsByProvider.get(provider);
        if (client == null) {
            throw new IllegalStateException("No PrSourceClient registered for provider " + provider);
        }
        return client;
    }
}
