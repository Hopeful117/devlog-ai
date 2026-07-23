package com.hopeful117.devlogai.repositorycontext.collector;

import com.hopeful117.devlogai.repositorycontext.ContextRequest;
import com.hopeful117.devlogai.repositorycontext.RepositoryContextLayer;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidence;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Order(10)
public class CurrentAnalysisContextCollector implements RepositoryContextCollector {
    private final EvidenceFactory evidenceFactory;

    public CurrentAnalysisContextCollector(EvidenceFactory evidenceFactory) {
        this.evidenceFactory = evidenceFactory;
    }

    @Override public String collectorId() { return "current-analysis"; }
    @Override public String collectorVersion() { return "v1"; }

    @Override
    public List<RepositoryEvidence> collect(ContextRequest request) {
        var analysis = request.analysisContext().analysis();
        return List.of(evidenceFactory.create(metadata(),
                RepositoryContextLayer.CURRENT_ANALYSIS, "ANALYSIS",
                "analysis:" + analysis.id(), request.intent().objective(),
                analysis.createdAt(), List.of(), null, null,
                analysis.id().toString(), request.budget().maximumSummaryCharacters()));
    }

    private EvidenceFactory.ContextRequestMetadata metadata() {
        return new EvidenceFactory.ContextRequestMetadata(
                collectorId(), collectorVersion(), "CORE_ANALYSIS");
    }
}
