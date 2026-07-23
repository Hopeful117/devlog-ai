package com.hopeful117.devlogai.history.dto;

import java.util.UUID;

public record HistoryImportResult(
        UUID repositoryId,
        String resolvedRevision,
        int discoveredCommits,
        int importedCommits,
        int existingCommits
) {
}
