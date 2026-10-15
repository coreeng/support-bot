package com.coreeng.supportbot.teamgroups;

import com.google.common.collect.ImmutableSortedSet;

/**
 * A named set of tenant teams — a client's business area, domain or tribe — that the Support
 * Summary can be focused on. Not to be confused with {@code teams.groups.GroupRef}, which is an
 * identity-provider group that a team's members are resolved from.
 *
 * @param code stable identifier, used in the URL
 * @param label the name shown in the UI
 * @param teamCodes the tenant team codes ({@code ticket.team}) the group covers, sorted so two
 *     groups with the same members compare (and digest) equal
 */
public record TeamGroup(String code, String label, ImmutableSortedSet<String> teamCodes) {}
