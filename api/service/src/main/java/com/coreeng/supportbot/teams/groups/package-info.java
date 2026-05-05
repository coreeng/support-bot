/**
 * Typed group-reference abstraction for the Support Bot.
 *
 * <p>{@link com.coreeng.supportbot.teams.groups.GroupRef} is the sealed interface every group
 * identifier (Slack user-group, Google group, Azure AD group, JWT claim value, or static
 * in-config key) parses into. It standardises the YAML config shape and gives downstream code
 * compile-time provider safety.
 *
 * <p>The push/pull asymmetry is intentional: provider fetchers (pull) implement
 * {@link com.coreeng.supportbot.teams.PlatformUsersFetcher}, while JWT membership (push) is
 * handled separately by {@code JwtGroupTeamMerger}. {@link com.coreeng.supportbot.teams.groups.GroupRef}
 * is the shared identifier across both, but the operations stay distinct.
 */
package com.coreeng.supportbot.teams.groups;
