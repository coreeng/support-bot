package com.coreeng.supportbot.teams;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * @param attributes extra values the platform teams fetcher extracted for this team (see the k8s-generic
 *     {@code attributes} config); empty when none were extracted or the team wasn't built from a platform team
 */
public record Team(String label, String code, ImmutableList<TeamType> types, ImmutableMap<String, String> attributes) {
    public Team(String label, String code, ImmutableList<TeamType> types) {
        this(label, code, types, ImmutableMap.of());
    }
}
