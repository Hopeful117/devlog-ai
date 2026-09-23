package com.hopeful117.devlogai.evidence.retrieval;

public interface GitCommitRetrievalPort {
    EvidencePage retrieve(EvidenceRetrievalQuery query);
}
