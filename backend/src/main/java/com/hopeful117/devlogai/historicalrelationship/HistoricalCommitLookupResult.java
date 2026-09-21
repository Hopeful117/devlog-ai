package com.hopeful117.devlogai.historicalrelationship;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record HistoricalCommitLookupResult(
        UUID projectId,
        CommitHash commitHash,
        List<StoryCommitReference> stories,
        List<EventCommitReference> events,
        boolean storiesTruncated,
        boolean eventsTruncated
) {
    public HistoricalCommitLookupResult {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(commitHash, "commitHash");
        Objects.requireNonNull(stories, "stories");
        Objects.requireNonNull(events, "events");
        stories = List.copyOf(stories);
        events = List.copyOf(events);
    }
}
