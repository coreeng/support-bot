package com.coreeng.supportbot.elevate;

public final class ElevateSnapshotChangedException extends RuntimeException {
    public ElevateSnapshotChangedException() {
        super("The Elevate snapshot changed; reload status before requesting more data");
    }
}
