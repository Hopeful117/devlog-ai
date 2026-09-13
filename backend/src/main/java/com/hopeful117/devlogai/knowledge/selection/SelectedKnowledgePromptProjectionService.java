package com.hopeful117.devlogai.knowledge.selection;

import com.hopeful117.devlogai.analysis.context.AnalysisContext;
import com.hopeful117.devlogai.knowledge.relation.entity.EntityType;
import com.hopeful117.devlogai.profile.dto.ProjectProfileResponse;
import com.hopeful117.devlogai.projectcontext.ProjectContextSnapshot;
import com.hopeful117.devlogai.repositorycontext.RepositoryContext;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidence;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidenceContent;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidenceSymbols;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class SelectedKnowledgePromptProjectionService {
    static final int MAX_RELATIONSHIP_HIGHLIGHTS = 20;

    private final ObjectMapper objectMapper;
    private final SemanticSectionComposer semanticSectionComposer;

    public SelectedKnowledgePromptProjectionService(ObjectMapper objectMapper,
            SemanticSectionComposer semanticSectionComposer) {
        this.objectMapper = objectMapper;
        this.semanticSectionComposer = semanticSectionComposer;
    }

    public Map<String, Object> toMap(SelectedKnowledge selectedKnowledge) {
        @SuppressWarnings("unchecked")
        Map<String, Object> projected = objectMapper.convertValue(
                project(selectedKnowledge), Map.class);
        // Diagnostics remain deterministic Core observability, not AI-facing knowledge.
        projected.remove("relationshipDiagnostics");
        return new LinkedHashMap<>(projected);
    }

    PromptProjection project(SelectedKnowledge selectedKnowledge) {
        RelationshipProjection relationships = relationshipProjection(selectedKnowledge);
        return new PromptProjection(
                selectedKnowledge.project(),
                selectedKnowledge.analysis(),
                selectedKnowledge.projectProfile(),
                selectedKnowledge.selectedFacts(),
                selectedKnowledge.selectedObservations(),
                selectedKnowledge.diagnostics(),
                selectedKnowledge.selectedInsights().stream().map(this::projectInsight).toList(),
                selectedKnowledge.existingArchitectureKnowledge(),
                selectedKnowledge.selectedEngineeringEvents(),
                selectedKnowledge.selectedHumanContextInputs(),
                relationships.highlights(),
                relationships.diagnostics(),
                semanticSectionComposer.compose(selectedKnowledge),
                projectRepositoryContext(selectedKnowledge.repositoryContext()),
                selectedKnowledge.evolutionContext(),
                selectedKnowledge.selectionMetadata(),
                selectedKnowledge.selectionDigest()
        );
    }

    private RelationshipProjection relationshipProjection(SelectedKnowledge selectedKnowledge) {
        Set<UUID> selectedInsightIds = new HashSet<>(selectedKnowledge.selectedInsights().stream()
                .map(SelectedKnowledge.InsightSnapshot::id)
                .toList());
        Set<UUID> selectedEngineeringEventIds = new HashSet<>(selectedKnowledge
                .selectedEngineeringEvents().stream()
                .map(ProjectContextSnapshot.EngineeringEventSnapshot::id)
                .toList());
        Set<String> selectedRepositoryEvidence = selectedKnowledge.repositoryContext() == null
                ? Set.of()
                : selectedKnowledge.repositoryContext().evidence().stream()
                .map(evidence -> evidence.kind() + "|" + evidence.reference())
                .collect(java.util.stream.Collectors.toSet());
        Comparator<ProjectContextSnapshot.KnowledgeRelationSnapshot> ordering = Comparator
                .comparing((ProjectContextSnapshot.KnowledgeRelationSnapshot relation) -> relation.relationType().name())
                .thenComparing(relation -> relation.sourceEntityType().name())
                .thenComparing(relation -> relation.sourceEntityId().toString())
                .thenComparing(relation -> relation.targetEntityType().name())
                .thenComparing(relation -> relation.targetEntityId().toString())
                .thenComparing(relation -> relation.id().toString());
        List<PromptRelationshipHighlight> highlights = new java.util.ArrayList<>();
        List<PromptRelationshipDiagnostic> diagnostics = new java.util.ArrayList<>();
        for (ProjectContextSnapshot.KnowledgeRelationSnapshot relation
                : selectedKnowledge.knowledgeRelations().stream().sorted(ordering).toList()) {
            boolean sourceProjected = isSelectedProjectedEndpoint(relation.sourceEntityType(),
                    relation.sourceEntityId(), selectedInsightIds, selectedEngineeringEventIds,
                    selectedRepositoryEvidence);
            boolean targetProjected = isSelectedProjectedEndpoint(relation.targetEntityType(),
                    relation.targetEntityId(), selectedInsightIds, selectedEngineeringEventIds,
                    selectedRepositoryEvidence);
            if (!sourceProjected || !targetProjected) {
                diagnostics.add(diagnostic(relation,
                        !sourceProjected && !targetProjected
                                ? "SOURCE_AND_TARGET_ENDPOINT_NOT_PROJECTED"
                                : !sourceProjected
                                ? "SOURCE_ENDPOINT_NOT_PROJECTED"
                                : "TARGET_ENDPOINT_NOT_PROJECTED"));
                continue;
            }
            if (highlights.size() >= MAX_RELATIONSHIP_HIGHLIGHTS) {
                diagnostics.add(diagnostic(relation, "RELATIONSHIP_BUDGET_EXCEEDED"));
                continue;
            }
            highlights.add(new PromptRelationshipHighlight(
                    relation.relationType().name(),
                    new PromptRelationshipEndpoint(relation.sourceEntityType().name(),
                            relation.sourceEntityId().toString()),
                    new PromptRelationshipEndpoint(relation.targetEntityType().name(),
                            relation.targetEntityId().toString())
            ));
        }
        return new RelationshipProjection(List.copyOf(highlights), List.copyOf(diagnostics));
    }

    private PromptRelationshipDiagnostic diagnostic(
            ProjectContextSnapshot.KnowledgeRelationSnapshot relation,
            String reason) {
        return new PromptRelationshipDiagnostic(relation.id().toString(),
                relation.relationType().name(),
                new PromptRelationshipEndpoint(relation.sourceEntityType().name(),
                        relation.sourceEntityId().toString()),
                new PromptRelationshipEndpoint(relation.targetEntityType().name(),
                        relation.targetEntityId().toString()), reason);
    }

    private boolean isSelectedProjectedEndpoint(
            EntityType entityType,
            UUID entityId,
            Set<UUID> selectedInsightIds,
            Set<UUID> selectedEngineeringEventIds,
            Set<String> selectedRepositoryEvidence
    ) {
        return switch (entityType) {
            case INSIGHT -> selectedInsightIds.contains(entityId)
                    || selectedRepositoryEvidence.contains("INSIGHT|insight:" + entityId);
            case ENGINEERING_EVENT -> selectedEngineeringEventIds.contains(entityId)
                    || selectedRepositoryEvidence.contains("ENGINEERING_EVENT|event:" + entityId);
            case DECISION -> selectedRepositoryEvidence.contains(
                    "DECISION|decision:" + entityId);
            case CHALLENGE -> selectedRepositoryEvidence.contains(
                    "CHALLENGE|challenge:" + entityId);
        };
    }

    private PromptInsightSnapshot projectInsight(SelectedKnowledge.InsightSnapshot insight) {
        return new PromptInsightSnapshot(
                insight.id(),
                insight.type(),
                insight.severity(),
                insight.title(),
                insight.content()
        );
    }

    private PromptRepositoryContext projectRepositoryContext(RepositoryContext repositoryContext) {
        if (repositoryContext == null) {
            return null;
        }
        return new PromptRepositoryContext(
                repositoryContext.contextVersion(),
                repositoryContext.profile(),
                repositoryContext.evidence().stream().map(this::projectEvidence).toList(),
                repositoryContext.warnings(),
                repositoryContext.contextDigest()
        );
    }

    private PromptRepositoryEvidence projectEvidence(RepositoryEvidence evidence) {
        return new PromptRepositoryEvidence(
                evidence.layer(),
                evidence.kind(),
                evidence.reference(),
                evidence.summary(),
                evidence.occurredAt(),
                evidence.relatedReferences(),
                projectContent(evidence.content()),
                projectSymbols(evidence.symbols())
        );
    }

    private PromptRepositoryContent projectContent(RepositoryEvidenceContent content) {
        if (content == null) return null;
        return new PromptRepositoryContent(
                content.status(),
                content.text(),
                content.reason(),
                content.policyId(),
                content.policyVersion(),
                content.revision(),
                content.allocationPolicyId(),
                content.allocationPolicyVersion(),
                content.allocationRank()
        );
    }

    private PromptRepositorySymbols projectSymbols(RepositoryEvidenceSymbols symbols) {
        if (symbols == null) return null;
        return new PromptRepositorySymbols(
                symbols.status(),
                symbols.reason(),
                symbols.policyId(),
                symbols.policyVersion(),
                symbols.extractorId(),
                symbols.extractorVersion(),
                symbols.revision(),
                symbols.allocationRank(),
                symbols.truncated(),
                symbols.returnedSymbolCount(),
                symbols.availableSymbolCount(),
                symbols.declarations()
        );
    }

    record PromptProjection(
            AnalysisContext.ProjectSnapshot project,
            AnalysisContext.AnalysisSnapshot analysis,
            ProjectProfileResponse projectProfile,
            List<AnalysisContext.FactSnapshot> selectedFacts,
            List<AnalysisContext.ObservationSnapshot> selectedObservations,
            SelectedKnowledge.DiagnosticSnapshot diagnostics,
            List<PromptInsightSnapshot> selectedInsights,
            List<SelectedKnowledge.ExistingArchitectureKnowledgeSnapshot> existingArchitectureKnowledge,
            List<com.hopeful117.devlogai.projectcontext.ProjectContextSnapshot.EngineeringEventSnapshot>
                    selectedEngineeringEvents,
            List<com.hopeful117.devlogai.projectcontext.ProjectContextSnapshot.HumanContextInputSnapshot>
                    selectedHumanContextInputs,
            List<PromptRelationshipHighlight> relationshipHighlights,
            List<PromptRelationshipDiagnostic> relationshipDiagnostics,
            List<SemanticSection.PromptSemanticSection> semanticSections,
            PromptRepositoryContext repositoryContext,
            AnalysisContext.EvolutionContext evolutionContext,
            SelectedKnowledge.SelectionMetadata selectionMetadata,
            String selectionDigest
    ) { }

    record PromptInsightSnapshot(
            java.util.UUID id,
            Object type,
            Object severity,
            String title,
            String content
    ) { }

    record PromptRelationshipHighlight(
            String relationType,
            PromptRelationshipEndpoint source,
            PromptRelationshipEndpoint target
    ) { }

    record PromptRelationshipDiagnostic(
            String relationId,
            String relationType,
            PromptRelationshipEndpoint source,
            PromptRelationshipEndpoint target,
            String reason
    ) { }

    record PromptRelationshipEndpoint(
            String entityType,
            String entityId
    ) { }

    private record RelationshipProjection(
            List<PromptRelationshipHighlight> highlights,
            List<PromptRelationshipDiagnostic> diagnostics
    ) { }

    record PromptRepositoryContext(
            String contextVersion,
            Object profile,
            List<PromptRepositoryEvidence> evidence,
            List<String> warnings,
            String contextDigest
    ) { }

    record PromptRepositoryEvidence(
            Object layer,
            String kind,
            String reference,
            String summary,
            Instant occurredAt,
            List<String> relatedReferences,
            PromptRepositoryContent content,
            PromptRepositorySymbols symbols
    ) { }

    record PromptRepositoryContent(
            Object status,
            String text,
            String reason,
            String policyId,
            String policyVersion,
            String revision,
            String allocationPolicyId,
            String allocationPolicyVersion,
            Integer allocationRank
    ) { }

    record PromptRepositorySymbols(
            Object status,
            String reason,
            String policyId,
            String policyVersion,
            String extractorId,
            String extractorVersion,
            String revision,
            Integer allocationRank,
            boolean truncated,
            int returnedSymbolCount,
            Integer availableSymbolCount,
            List<RepositoryEvidenceSymbols.JavaDeclaration> declarations
    ) { }
}
