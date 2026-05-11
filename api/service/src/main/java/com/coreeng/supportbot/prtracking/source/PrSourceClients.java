package com.coreeng.supportbot.prtracking.source;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PrSourceClients {

    private final Map<Provider, PrSourceClient> clientsByProvider;

    public PrSourceClients(Map<Provider, PrSourceClient> clientsByProvider) {
        this.clientsByProvider = new EnumMap<>(clientsByProvider);
    }

    /**
     * Aggregates a list of adapters into a provider keyed lookup. Two adapters reporting the
     * same provider is a config error — the caller has wired conflicting beans and one would
     * silently shadow the other.
     */
    public PrSourceClients(List<PrSourceClient> clients) {
        Map<Provider, PrSourceClient> byProvider = new HashMap<>();
        for (PrSourceClient client : clients) {
            PrSourceClient existing = byProvider.put(client.provider(), client);
            if (existing != null) {
                throw new IllegalStateException("Multiple PrSourceClient beans registered for provider "
                        + client.provider() + ": " + existing.getClass().getName() + " and "
                        + client.getClass().getName());
            }
        }
        this.clientsByProvider = new EnumMap<>(byProvider);
    }

    public PrSourceClient forProvider(Provider provider) {
        PrSourceClient client = clientsByProvider.get(provider);
        if (client == null) {
            throw new IllegalStateException("No PrSourceClient registered for provider " + provider);
        }
        return client;
    }
}
