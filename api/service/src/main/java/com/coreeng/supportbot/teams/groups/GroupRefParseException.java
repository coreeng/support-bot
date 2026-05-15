package com.coreeng.supportbot.teams.groups;

/**
 * Thrown when a string cannot be parsed into a {@link GroupRef} — blank input, unknown
 * provider prefix, or a record component that fails its own validity check.
 *
 * <p>Catch this in callers that source group references from external systems (e.g. K8s
 * resources, CEL expressions) so a single malformed value can be logged and skipped without
 * aborting the surrounding fetch.
 */
public class GroupRefParseException extends RuntimeException {
    public GroupRefParseException(String message) {
        super(message);
    }
}
