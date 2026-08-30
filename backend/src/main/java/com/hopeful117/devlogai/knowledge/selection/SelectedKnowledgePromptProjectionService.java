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
        return new LinkedHashMap<>(projected);
    }

    PromptProjection project(SelectedKnowledge selectedKnowledge) {
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
                buildRelationshipHighlights(selectedKnowledge),
                semanticSectionComposer.compose(selectedKnowledge),
                projectRepositoryContext(selectedKnowledge.repositoryContext()),
                selectedKnowledge.evolutionContext(),
                selectedKnowledge.selectionMetadata(),
                selectedKnowledge.selectionDigest()
        );
    }

    private List<PromptRelationshipHighlight> buildRelationshipHighlights(
            SelectedKnowledge selectedKnowledge) {
        Set<UUID> selectedInsightIds = new HashSet<>(selectedKnowledge.selectedInsights().stream()
                .map(SelectedKnowledge.InsightSnapshot::id)
                .toList());
        Set<UUID> selectedEngineeringEventIds = new HashSet<>(selectedKnowledge
                .selectedEngineeringEvents().stream()
                .map(ProjectContextSnapshot.EngineeringEventSnapshot::id)
                .toList());
        Comparator<ProjectContextSnapshot.KnowledgeRelationSnapshot> ordering = Comparator
                .comparing((ProjectContextSnapshot.KnowledgeRelationSnapshot relation) -> relation.relationType().name())
                .thenComparing(relation -> relation.sourceEntityType().name())
                .thenComparing(relation -> relation.sourceEntityId().toString())
                .thenComparing(relation -> relation.targetEntityType().name())
                .thenComparing(relation -> relation.targetEntityId().toString())
                .thenComparing(relation -> relation.id().toString());
        return selectedKnowledge.knowledgeRelations().stream()
                .filter(relation -> isPolicyAEligible(relation, selectedInsightIds,
                        selectedEngineeringEventIds))
                .sorted(ordering)
                .limit(MAX_RELATIONSHIP_HIGHLIGHTS)
                .map(relation -> new PromptRelationshipHighlight(
                        relation.relationType().name(),
                        new PromptRelationshipEndpoint(relation.sourceEntityType().name(),
                                relation.sourceEntityId().toString()),
                        new PromptRelationshipEndpoint(relation.targetEntityType().name(),
                                relation.targetEntityId().toString())
                ))
                .toList();
    }

    private boolean isPolicyAEligible(
            ProjectContextSnapshot.KnowledgeRelationSnapshot relation,
            Set<UUID> selectedInsightIds,
            Set<UUID> selectedEngineeringEventIds
    ) {
        return isSelectedProjectedEndpoint(relation.sourceEntityType(), relation.sourceEntityId(),
                selectedInsightIds, selectedEngineeringEventIds)
                && isSelectedProjectedEndpoint(relation.targetEntityType(), relation.targetEntityId(),
                selectedInsightIds, selectedEngineeringEventIds);
    }

    private boolean isSelectedProjectedEndpoint(
            EntityType entityType,
            UUID entityId,
            Set<UUID> selectedInsightIds,
            Set<UUID> selectedEngineeringEventIds
    ) {
        return switch (entityType) {
            case INSIGHT -> selectedInsightIds.contains(entityId);
            case ENGINEERING_EVENT -> selectedEngineeringEventIds.contains(entityId);
            case DECISION, CHALLENGE -> false;
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

    record PromptRelationshipEndpoint(
            String entityType,
            String entityId
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
