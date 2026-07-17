package com.coreeng.supportbot.elevate;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record ElevateStoredStatus(
        ElevateSyncState state,
        @Nullable UUID snapshotVersion,
        ElevateCounts counts,
        ElevateIntegrityCounts integrity) {}
