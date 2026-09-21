package com.hopeful117.devlogai.historicalrelationship;

import java.util.List;
import java.util.Objects;

record StoryCommitLookupResult(
        List<StoryCommitReference> references,
        boolean truncated
) {
    StoryCommitLookupResult {
        Objects.requireNonNull(references, "references");
        references = List.copyOf(references);
    }
}
