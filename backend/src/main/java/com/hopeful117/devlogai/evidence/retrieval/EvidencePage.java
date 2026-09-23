package com.hopeful117.devlogai.evidence.retrieval;

import java.util.List;
import java.util.Objects;

public record EvidencePage(
        List<EvidenceCandidate> candidates,
        int page,
        int pageSize,
        long totalMatches,
        boolean hasNext
) {
    public EvidencePage {
        Objects.requireNonNull(candidates, "candidates");
        candidates = List.copyOf(candidates);
        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative");
        }
        if (pageSize < 1 || pageSize > SharedEvidenceRetrievalBoundary.MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("pageSize must be between 1 and 100");
        }
        if (totalMatches < 0) {
            throw new IllegalArgumentException("totalMatches must not be negative");
        }
        if (candidates.size() > pageSize) {
            throw new IllegalArgumentException("candidates must not exceed pageSize");
        }
    }
}
