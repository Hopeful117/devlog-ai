package com.hopeful117.devlogai.repositorycontext.collector;

import com.hopeful117.devlogai.repositorycontext.ContextRequest;
import com.hopeful117.devlogai.repositorycontext.RepositoryContextLayer;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidence;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
@Order(40)
public class ProjectKnowledgeContextCollector implements RepositoryContextCollector {
    private final EvidenceFactory evidenceFactory;

    public ProjectKnowledgeContextCollector(EvidenceFactory evidenceFactory) {
        this.evidenceFactory = evidenceFactory;
    }

    @Override public String collectorId() { return "project-knowledge"; }
    @Override public String collectorVersion() { return "v1"; }

    @Override
    public List<RepositoryEvidence> collect(ContextRequest request) {
        List<RepositoryEvidence> result = new ArrayList<>();
        request.analysisContext().relatedDecisions().forEach(value -> result.add(create(
                request, RepositoryContextLayer.ADR, "DECISION", "decision:" + value.id(),
                value.title() + " — " + Objects.toString(value.choice(), ""),
                value.createdAt(), 800, value.id().toString(), List.of())));
        request.analysisContext().recentMilestones().forEach(value -> result.add(create(
                request, RepositoryContextLayer.ROADMAP, "MILESTONE",
                "milestone:" + value.id(), value.name() + " — " + value.status(),
                value.startedAt(), 760, value.id().toString(), List.of())));
        request.validatedInsights().forEach(value -> result.add(create(
                request, RepositoryContextLayer.VALIDATED_INSIGHT, "INSIGHT",
                "insight:" + value.getId(), value.getTitle() + " — " + value.getContent(),
                value.getCreatedAt(), 850, value.getId().toString(),
                List.of("analysis:" + value.getAnalysis().getId()))));
        request.analysisContext().relatedAnalyses().forEach(value -> result.add(create(
                request, RepositoryContextLayer.PREVIOUS_ANALYSIS, "ANALYSIS",
                "analysis:" + value.id(), value.type() + " " + value.status(),
                value.createdAt(), 450, value.id().toString(), List.of())));
        request.analysisContext().architectureArtifacts().forEach(value -> result.add(
                evidenceFactory.create(metadata(),
                        RepositoryContextLayer.PROJECT_DOCUMENTATION, "ARTIFACT",
                        "artifact:" + value.id(),
                        value.name() + " — " + Objects.toString(value.description(), ""),
                        value.createdAt(), 600,
                        value.path() == null ? List.of()
                                : List.of("repository:" + value.path()),
                        null, value.path(), value.id().toString(),
                        request.budget().maximumSummaryCharacters())));
        return List.copyOf(result);
    }

    private RepositoryEvidence create(
            ContextRequest request,
            RepositoryContextLayer layer,
            String kind,
            String reference,
            String summary,
            java.time.Instant timestamp,
            int relevance,
            String identifier,
            List<String> related
    ) {
        return evidenceFactory.create(metadata(), layer, kind, reference, summary,
                timestamp, relevance, related, null, null, identifier,
                request.budget().maximumSummaryCharacters());
    }

    private EvidenceFactory.ContextRequestMetadata metadata() {
        return new EvidenceFactory.ContextRequestMetadata(
                collectorId(), collectorVersion(), "CORE_KNOWLEDGE");
    }
}
