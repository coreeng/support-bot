package com.coreeng.supportbot.escalation;

/** Constants for the {@code escalation.source} column which tracks who created the escalation. */
public final class EscalationSource {
    /** Not auto-escalated typically triggered by a support engineer. */
    public static final String MANUAL = "manual";

    /** Automatically escalated by the PR tracking system when an SLA is breached. */
    public static final String BOT = "bot";

    private EscalationSource() {}
}
