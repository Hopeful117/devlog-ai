package com.hopeful117.devlogai.repositorycontext.selection;

import com.hopeful117.devlogai.repositorycontext.ContextRequest;
import com.hopeful117.devlogai.repositorycontext.RepositoryContext;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidence;

import java.util.List;

public interface EvidenceSelector {
    SelectionResult select(List<RepositoryEvidence> ranked, ContextRequest request);

    record SelectionResult(
            List<RepositoryEvidence> selected,
            List<RepositoryContext.SelectionDecision> decisions,
            int usedTokens
    ) {
        public SelectionResult {
            selected = List.copyOf(selected);
            decisions = List.copyOf(decisions);
        }
    }
}
