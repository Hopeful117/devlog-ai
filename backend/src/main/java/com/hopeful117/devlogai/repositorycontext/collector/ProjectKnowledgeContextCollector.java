package com.hopeful117.devlogai.repositorycontext.collector;

import com.hopeful117.devlogai.repositorycontext.ContextRequest;
import com.hopeful117.devlogai.repositorycontext.RepositoryContextLayer;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidence;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        request.analysisContext().relatedDecisions().forEach(value -> {
            String rationale = value.rationale();
            String summary;
            if (rationale == null || rationale.isBlank()) {
                summary = value.title() + " — " + Objects.toString(value.choice(), "");
            } else {
                summary = value.title() + " — " + Objects.toString(value.choice(), "")
                        + " — " + rationale;
            }
            result.add(create(
                    request, new KnowledgeEvidence(RepositoryContextLayer.ADR, "DECISION", "decision:" + value.id(),
                            summary,
                            value.createdAt(), value.id().toString(), List.of())));
        });
        request.analysisContext().recentMilestones().forEach(value -> result.add(create(
                request, new KnowledgeEvidence(RepositoryContextLayer.ROADMAP, "MILESTONE",
                "milestone:" + value.id(), value.name() + " — " + value.status(),
                value.startedAt(), value.id().toString(), List.of()))));
        request.validatedInsights().forEach(value -> result.add(create(
                request, new KnowledgeEvidence(RepositoryContextLayer.VALIDATED_INSIGHT, "INSIGHT",
                "insight:" + value.getId(), value.getTitle() + " — " + value.getContent(),
                value.getCreatedAt(), value.getId().toString(),
                List.of("analysis:" + value.getAnalysis().getId())))));
        request.analysisContext().relatedAnalyses().forEach(value -> result.add(create(
                request, new KnowledgeEvidence(RepositoryContextLayer.PREVIOUS_ANALYSIS, "ANALYSIS",
                "analysis:" + value.id(), value.type() + " " + value.status(),
                value.createdAt(), value.id().toString(), List.of()))));
        request.analysisContext().architectureArtifacts().forEach(value -> result.add(
                evidenceFactory.create(metadata(), new EvidenceFactory.EvidenceInput(
                        RepositoryContextLayer.PROJECT_DOCUMENTATION, "ARTIFACT",
                        "artifact:" + value.id(),
                        value.name() + " — " + Objects.toString(value.description(), ""),
                        value.createdAt(),
                        value.path() == null ? List.of()
                                : List.of("repository:" + value.path()),
                        null, value.path(), value.id().toString()),
                        request.budget().maximumSummaryCharacters())));

request.analysisContext().engineeringStories().forEach(story -> {
            RepositoryEvidence evidence = evidenceFactory.create(metadata(), new EvidenceFactory.EvidenceInput(
                    RepositoryContextLayer.ROADMAP, "ENGINEERING_STORY",
                    "story:" + story.id(),
                    story.title(),
                    story.createdAt(),
                    List.of(),
                    "ENGINEERING_STORY", story.storyPath(),
                    story.id().toString()),
                    request.budget().maximumSummaryCharacters());
            Map<String, String> extractionMetadata = new LinkedHashMap<>(evidence.extractionMetadata());
            if (story.storyNumber() != null) {
                extractionMetadata.put("storyNumber", story.storyNumber().toString());
            }
            if (story.status() != null) {
                extractionMetadata.put("status", story.status());
            }
            if (story.baseCommit() != null) {
                extractionMetadata.put("baseCommit", story.baseCommit());
            }
            if (story.targetCommit() != null) {
                extractionMetadata.put("targetCommit", story.targetCommit());
            }
            result.add(evidence.withExtractionMetadata(extractionMetadata));
        });

        return List.copyOf(result);
    }

    private RepositoryEvidence create(
            ContextRequest request,
            KnowledgeEvidence value
    ) {
        return evidenceFactory.create(metadata(), new EvidenceFactory.EvidenceInput(
                value.layer(), value.kind(), value.reference(), value.summary(),
                value.timestamp(), value.related(), null, null, value.identifier()),
                request.budget().maximumSummaryCharacters());
    }

    private EvidenceFactory.ContextRequestMetadata metadata() {
        return new EvidenceFactory.ContextRequestMetadata(
                collectorId(), collectorVersion(), "CORE_KNOWLEDGE");
    }

    private record KnowledgeEvidence(
            RepositoryContextLayer layer,
            String kind,
            String reference,
            String summary,
            java.time.Instant timestamp,
            String identifier,
            List<String> related
    ) {
    }
}
