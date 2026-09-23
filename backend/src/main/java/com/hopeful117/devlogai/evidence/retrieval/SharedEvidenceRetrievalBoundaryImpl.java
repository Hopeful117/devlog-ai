package com.hopeful117.devlogai.evidence.retrieval;

import java.util.Objects;

/**
 * Common Slice 0 validation and Git-family delegation without Spring wiring.
 * The Git adapter is intentionally introduced by the next implementation slice.
 */
public final class SharedEvidenceRetrievalBoundaryImpl
        implements SharedEvidenceRetrievalBoundary {
    private final GitCommitRetrievalPort gitCommitRetrievalPort;

    public SharedEvidenceRetrievalBoundaryImpl(GitCommitRetrievalPort gitCommitRetrievalPort) {
        this.gitCommitRetrievalPort = Objects.requireNonNull(
                gitCommitRetrievalPort, "gitCommitRetrievalPort");
    }

    @Override
    public EvidencePage retrieve(EvidenceRetrievalQuery query) {
        validate(query);
        return gitCommitRetrievalPort.retrieve(query);
    }

    private void validate(EvidenceRetrievalQuery query) {
        if (query == null || query.scope() == null || query.scope().projectId() == null) {
            throw failure(EvidenceRetrievalFailureCode.INVALID_SCOPE,
                    "projectId is required");
        }
        if (query.family() != EvidenceRetrievalFamily.GIT_COMMIT) {
            throw failure(EvidenceRetrievalFailureCode.UNSUPPORTED_FAMILY,
                    "Only GIT_COMMIT retrieval is supported");
        }
        if (!hasValidLexicalTerm(query.lexicalQuery())) {
            throw failure(EvidenceRetrievalFailureCode.INVALID_QUERY,
                    "lexicalQuery must contain an alphanumeric term of 2+ characters");
        }
        if (query.page() < 0
                || query.pageSize() < 1
                || query.pageSize() > MAX_PAGE_SIZE) {
            throw failure(EvidenceRetrievalFailureCode.INVALID_PAGE,
                    "page must be non-negative and pageSize must be between 1 and 100");
        }
    }

    private boolean hasValidLexicalTerm(String query) {
        if (query == null || query.isBlank()) return false;
        for (String term : query.toLowerCase(java.util.Locale.ROOT).split("[^a-z0-9]+")) {
            if (term.length() >= 2) return true;
        }
        return false;
    }

    private EvidenceRetrievalException failure(
            EvidenceRetrievalFailureCode code,
            String message
    ) {
        return new EvidenceRetrievalException(code, message);
    }
}
