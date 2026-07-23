package com.hopeful117.devlogai.history.model;

import java.time.Instant;
import java.util.List;

public record GitCommitData(
        String commitHash,
        List<String> parentHashes,
        String authorName,
        String authorEmail,
        Instant authoredAt,
        Instant committedAt,
        String subject,
        String fullMessage,
        List<GitFileChange> changedFiles,
        DiffStatistics statistics
) {
    public boolean rootCommit() {
        return parentHashes.isEmpty();
    }

    public boolean mergeCommit() {
        return parentHashes.size() > 1;
    }

    public String firstParentHash() {
        return rootCommit() ? null : parentHashes.getFirst();
    }
}
