package com.hopeful117.devlogai.history.context;

import com.hopeful117.devlogai.history.model.DiffStatistics;
import com.hopeful117.devlogai.history.model.FileChangeType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Bounded deterministic context. It is evidence for a future versioned Intent,
 * never a trusted interpretation (ADR-035/ADR-036).
 */
public record CommitDiffAnalysisContext(
        UUID projectId,
        UUID repositoryId,
        String commitHash,
        String firstParentHash,
        List<String> parentHashes,
        boolean rootCommit,
        boolean mergeCommit,
        String commitMessage,
        Instant committedAt,
        List<ChangedFileContext> changedFiles,
        DiffStatistics statistics,
        List<String> candidateAdrReferences,
        List<String> candidateRoadmapReferences,
        List<String> evidenceReferences,
        boolean truncated,
        List<String> warnings
) {
    public record ChangedFileContext(
            FileChangeType changeType,
            String oldPath,
            String newPath,
            boolean binary,
            int insertions,
            int deletions,
            String language,
            String category,
            boolean excludedFromAnalysis,
            String exclusionReason,
            String evidenceReference
    ) {
    }
}
