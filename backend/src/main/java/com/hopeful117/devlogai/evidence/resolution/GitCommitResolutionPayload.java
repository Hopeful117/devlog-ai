package com.hopeful117.devlogai.evidence.resolution;

import java.time.Instant;
import java.util.List;

public record GitCommitResolutionPayload(
        String commitHash,
        String subject,
        String fullMessage,
        String authorName,
        String authorEmail,
        Instant authoredAt,
        Instant committedAt,
        boolean rootCommit,
        boolean mergeCommit,
        List<String> parentHashes,
        List<ChangedFileResolution> changedFiles
) implements EvidenceResolutionPayload {
    public GitCommitResolutionPayload {
        parentHashes = List.copyOf(parentHashes);
        changedFiles = List.copyOf(changedFiles);
    }

    public record ChangedFileResolution(
            String changeType,
            String oldPath,
            String newPath,
            boolean binary,
            int insertions,
            int deletions
    ) {
    }
}
