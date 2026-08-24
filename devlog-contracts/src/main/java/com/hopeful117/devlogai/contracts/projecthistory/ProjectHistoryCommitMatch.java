package com.hopeful117.devlogai.contracts.projecthistory;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Compact discovery result for one commit matching a project history search.
 * Deliberately excludes diffs and classifications: detailed inspection
 * belongs to the commit-context resource referenced by {@code resource}.
 */
public record ProjectHistoryCommitMatch(

        String commitSha,
        String subject,
        String authorName,
        Instant committedAt,
        UUID repositoryId,
        int relevance,
        List<ProjectHistoryMatch> matches,
        String resource
) {
    public ProjectHistoryCommitMatch {
        matches = matches == null ? List.of() : List.copyOf(matches);
    }
}
