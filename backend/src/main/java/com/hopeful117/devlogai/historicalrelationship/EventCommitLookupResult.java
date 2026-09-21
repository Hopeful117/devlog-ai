package com.hopeful117.devlogai.historicalrelationship;

import java.util.List;
import java.util.Objects;

record EventCommitLookupResult(
        List<EventCommitReference> references,
        boolean truncated
) {
    EventCommitLookupResult {
        Objects.requireNonNull(references, "references");
        references = List.copyOf(references);
    }
}
