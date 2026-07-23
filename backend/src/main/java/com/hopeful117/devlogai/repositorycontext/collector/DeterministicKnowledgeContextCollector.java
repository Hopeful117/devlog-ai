package com.hopeful117.devlogai.repositorycontext.collector;

import com.hopeful117.devlogai.analysis.context.AnalysisContext;
import com.hopeful117.devlogai.repositorycontext.ContextRequest;
import com.hopeful117.devlogai.repositorycontext.RepositoryContextLayer;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidence;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
@Order(20)
public class DeterministicKnowledgeContextCollector
        implements RepositoryContextCollector {
    private final EvidenceFactory evidenceFactory;

    public DeterministicKnowledgeContextCollector(EvidenceFactory evidenceFactory) {
        this.evidenceFactory = evidenceFactory;
    }

    @Override public String collectorId() { return "deterministic-knowledge"; }
    @Override public String collectorVersion() { return "v1"; }

    @Override
    public List<RepositoryEvidence> collect(ContextRequest request) {
        List<RepositoryEvidence> evidence = new ArrayList<>();
        for (AnalysisContext.FactSnapshot fact : request.analysisContext().facts()) {
            String location = fact.evidenceReferences().stream().findFirst().orElse(fact.source());
            evidence.add(evidenceFactory.create(metadata(), layer(location), "FACT",
                    location == null ? "fact:" + fact.id() : location,
                    fact.content(), fact.detectedAt(),
                    List.of("fact:" + fact.id()), location, location,
                    fact.id().toString(), request.budget().maximumSummaryCharacters()));
        }
        for (AnalysisContext.ObservationSnapshot observation
                : request.analysisContext().observations()) {
            evidence.add(evidenceFactory.create(metadata(),
                    RepositoryContextLayer.RELATED_SOURCE_CODE, "OBSERVATION",
                    "observation:" + observation.id(), observation.content(),
                    observation.createdAt(),
                    observation.supportingFactIds().stream()
                            .map(id -> "fact:" + id).toList(),
                    null, null, observation.id().toString(),
                    request.budget().maximumSummaryCharacters()));
        }
        return List.copyOf(evidence);
    }

    private RepositoryContextLayer layer(String path) {
        String value = path == null ? "" : path.toLowerCase(Locale.ROOT);
        if (value.contains("adr-") || value.contains("/decisions/"))
            return RepositoryContextLayer.ADR;
        if (value.contains("roadmap")) return RepositoryContextLayer.ROADMAP;
        if (value.endsWith(".md") || value.contains("readme"))
            return RepositoryContextLayer.PROJECT_DOCUMENTATION;
        if (value.contains("/test") || value.endsWith("test.java")
                || value.endsWith(".spec.ts")) return RepositoryContextLayer.RELATED_SOURCE_CODE;
        return RepositoryContextLayer.RELATED_SOURCE_CODE;
    }

    private EvidenceFactory.ContextRequestMetadata metadata() {
        return new EvidenceFactory.ContextRequestMetadata(
                collectorId(), collectorVersion(), "DETERMINISTIC_EXTRACTION");
    }
}
