package com.hopeful117.devlogai.evidence.retrieval;

public record EvidenceRetrievalQuery(
        EvidenceRetrievalScope scope,
        EvidenceRetrievalFamily family,
        String lexicalQuery,
        int page,
        int pageSize
) {
}
