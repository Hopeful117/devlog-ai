package com.hopeful117.devlogai.knowledge.selection;

import com.hopeful117.devlogai.analysis.context.AnalysisContext;
import com.hopeful117.devlogai.profile.dto.ProjectProfileResponse;
import com.hopeful117.devlogai.repositorycontext.RepositoryContext;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidence;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidenceContent;
import com.hopeful117.devlogai.repositorycontext.RepositoryEvidenceSymbols;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SelectedKnowledgePromptProjectionService {

    private final ObjectMapper objectMapper;

    public SelectedKnowledgePromptProjectionService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
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
                selectedKnowledge.selectedInsights(),
                selectedKnowledge.existingArchitectureKnowledge(),
                selectedKnowledge.selectedEngineeringEvents(),
                projectRepositoryContext(selectedKnowledge.repositoryContext()),
                selectedKnowledge.evolutionContext(),
                selectedKnowledge.selectionMetadata(),
                selectedKnowledge.selectionDigest()
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
            List<SelectedKnowledge.InsightSnapshot> selectedInsights,
            List<SelectedKnowledge.ExistingArchitectureKnowledgeSnapshot> existingArchitectureKnowledge,
            List<com.hopeful117.devlogai.projectcontext.ProjectContextSnapshot.EngineeringEventSnapshot>
                    selectedEngineeringEvents,
            PromptRepositoryContext repositoryContext,
            AnalysisContext.EvolutionContext evolutionContext,
            SelectedKnowledge.SelectionMetadata selectionMetadata,
            String selectionDigest
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
