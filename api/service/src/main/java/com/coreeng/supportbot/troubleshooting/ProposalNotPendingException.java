package com.coreeng.supportbot.troubleshooting;

public class ProposalNotPendingException extends RuntimeException {
    public ProposalNotPendingException(long proposalSeq) {
        super("Proposal " + proposalSeq + " is not waiting for a decision");
    }
}
