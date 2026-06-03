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
            PrSourceClient existing = byProvider.put(client.getProvider(), client);
            if (existing != null) {
                throw new IllegalStateException("Multiple PrSourceClient beans registered for provider "
                        + client.getProvider() + ": " + existing.getClass().getName() + " and "
                        + client.getClass().getName());
            }
        }
        this.clientsByProvider = new EnumMap<>(byProvider);
    }

    /**
     * Looks up the adapter for a provider. Throws {@link PrSourceException} (not a hard error) when
     * none is registered: a tracking row can outlive its provider's configuration — e.g. all GitLab
     * repos are removed from config, dropping the {@code GitLabPrSourceClient} bean, while active
     * {@code provider='gitlab'} rows remain. Surfacing it as a source failure lets the lifecycle
     * poller skip the record gracefully instead of logging an error every cycle.
     */
    public PrSourceClient forProvider(Provider provider) {
        PrSourceClient client = clientsByProvider.get(provider);
        if (client == null) {
            throw new PrSourceException("No PrSourceClient registered for provider " + provider
                    + " — its repositories were likely removed from configuration while tracking rows remain");
        }
        return client;
    }
}
