package com.hopeful117.devlogai.evidence.retrieval;

public interface SharedEvidenceRetrievalBoundary {
    int MAX_PAGE_SIZE = 100;

    EvidencePage retrieve(EvidenceRetrievalQuery query);
}
