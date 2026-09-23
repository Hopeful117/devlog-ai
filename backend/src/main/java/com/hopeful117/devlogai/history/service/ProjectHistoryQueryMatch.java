package com.hopeful117.devlogai.history.service;

import com.hopeful117.devlogai.contracts.projecthistory.ProjectHistoryMatch;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * History-owned match data shared by public history search and retrieval.
 * It intentionally carries no resolved commit payload.
 */
public record ProjectHistoryQueryMatch(
        String commitSha,
        String subject,
        String authorName,
        Instant committedAt,
        UUID sourceId,
        int relevance,
        List<ProjectHistoryMatch> matches
) {
    public ProjectHistoryQueryMatch {
        Objects.requireNonNull(commitSha, "commitSha");
        Objects.requireNonNull(committedAt, "committedAt");
        Objects.requireNonNull(sourceId, "sourceId");
        matches = matches == null ? List.of() : List.copyOf(matches);
    }
}
