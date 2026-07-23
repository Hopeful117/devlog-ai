package com.hopeful117.devlogai.repositorycontext.ranking;

import com.hopeful117.devlogai.repositorycontext.ContextRequest;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidence;

import java.util.List;

public interface EvidenceRanker {
    List<RepositoryEvidence> rank(List<RepositoryEvidence> evidence, ContextRequest request);
}
