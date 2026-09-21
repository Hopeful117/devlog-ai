package com.hopeful117.devlogai.historicalrelationship;

import java.util.Objects;
import java.util.UUID;

public record HistoricalCommitLookup(
        UUID projectId,
        CommitHash commitHash
) {
    public HistoricalCommitLookup {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(commitHash, "commitHash");
    }
}
