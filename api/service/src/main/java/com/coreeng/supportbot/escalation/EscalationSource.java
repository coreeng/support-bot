package com.coreeng.supportbot.escalation;

/** Tracks who created the escalation. Stored as the enum {@code name()} in the DB. */
public enum EscalationSource {
    /** Automatically escalated by the PR tracking system when an SLA is breached. */
    bot,

    /** Not auto-escalated — typically triggered by a support engineer. */
    manual
}
