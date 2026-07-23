package com.hopeful117.devlogai.history.dto;

import com.hopeful117.devlogai.history.model.FileChangeType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProjectCommitResponse(
        UUID id,
        UUID projectId,
        UUID repositoryId,
        String commitHash,
        List<String> parentHashes,
        String authorName,
        String authorEmail,
        Instant authoredAt,
        Instant committedAt,
        String subject,
        String fullMessage,
        boolean rootCommit,
        boolean mergeCommit,
        int filesChanged,
        int insertions,
        int deletions,
        int binaryFiles,
        Instant importedAt,
        List<ChangedFileResponse> changedFiles
) {
    public record ChangedFileResponse(
            FileChangeType changeType,
            String oldPath,
            String newPath,
            boolean binary,
            int insertions,
            int deletions
    ) {
    }
}
